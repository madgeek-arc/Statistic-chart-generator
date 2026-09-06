package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class StatsDBRepository implements StatsCache {

    public static final String CACHE_DB_NAME = "cache";

    // A LONGVARCHAR column / cast resolves to VARCHAR(16M) in HSQLDB. Stay safely below that
    // ceiling: an oversized payload is skipped (logged) rather than failing the whole write.
    private static final int MAX_CACHE_VALUE_CHARS = 15 * 1024 * 1024;

    @Value("${statstool.cache.enabled:true}")
    private boolean enableCache;

    private final Logger log = LogManager.getLogger(this.getClass());

    private final JdbcTemplate jdbcTemplate;

    public StatsDBRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void postInit() {
        DatasourceContext.setContext(CACHE_DB_NAME);

        log.debug("Creating cache table");
        jdbcTemplate.execute("create table if not exists cache_entry (" +
                        "key varchar(64) not null," +
                        "result longvarchar not null, " +
                        "shadow longvarchar, " +
                        "query longvarchar not null," +
                        "created timestamp default now() not null, " +
                        "updated timestamp default now() not null, " +
                        "total_hits int default 0 not null," +
                        "session_hits int default 0 not null," +
                        "pinned boolean default false not null," +
                        "exectime int default 0 not null," +
                        "queuetime int default 0 not null," +
                        "profile varchar(255) not null," +
                        "fresh boolean default false not null)" );

        // Migrate existing tables that predate the queuetime column
        try {
            jdbcTemplate.execute("alter table cache_entry add column if not exists queuetime int default 0 not null");
        } catch (Exception ignored) {
            // Column may already exist or DB may not support IF NOT EXISTS; safe to ignore
        }

        // Migrate existing tables created by older builds with narrow VARCHAR columns.
        // `create table if not exists` above never re-applies the widened types to a
        // persisted cache_entry, so a stale /tmp/cache volume keeps e.g. query VARCHAR(10000)
        // and overflows with "string data, right truncation" on large cached SQL.
        // Each ALTER is idempotent (a no-op when the column already has the target type).
        for (String migration : new String[]{
                "alter table cache_entry alter column query longvarchar",
                "alter table cache_entry alter column result longvarchar",
                "alter table cache_entry alter column shadow longvarchar",
                "alter table cache_entry alter column profile varchar(255)",
                "alter table cache_entry add column if not exists valid boolean default true not null",
                "alter table cache_entry add column if not exists fresh boolean default false not null",
        }) {
            try {
                jdbcTemplate.execute(migration);
            } catch (Exception ignored) {
                // Already the target type, or the DB rejects a no-op change; safe to ignore
            }
        }
        log.info("cache_entry schema migration checks complete");

        jdbcTemplate.execute("create index if not exists key_idx on cache_entry(key)");
    }

    @Override
    public boolean isEnabled() {
        return enableCache;
    }

    @Override
    public Result get(String key) throws Exception {
        if (!enableCache)
            return null;

        DatasourceContext.setContext(CACHE_DB_NAME);

        // Increment hit counters unconditionally — reflects true demand even for stale misses.
        // 0 rows = key absent → true cache miss.
        int rows = jdbcTemplate.update(
                "update cache_entry set total_hits=total_hits+1, session_hits=session_hits+1 where key=?", key);

        if (rows == 0)
            return null;

        // Entry exists: return result only if fresh; stale returns null (caller re-executes).
        return jdbcTemplate.queryForObject("select result, fresh from cache_entry where key=?", new Object[]{key}, (resultSet, i) -> {
            if (!resultSet.getBoolean("fresh"))
                return null; // stale miss — counters already incremented above
            try {
                return new ObjectMapper().readValue(resultSet.getString("result"), Result.class);
            } catch (IOException e) {
                log.error("Error getting entry with key " + key, e);
            }
            throw new RuntimeException("Error deserializing cached result for key " + key);
        });
    }

    public void dropCache(String profile) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);

        String sql = "delete from cache_entry";

        if (profile != null && !profile.isEmpty()) {
            sql += " where profile=?";
            jdbcTemplate.update(sql, profile);
        } else {
            jdbcTemplate.execute(sql);
        }
    }

    @Override
    public void save(QueryWithParameters fullSqlQuery, Result result, int execTime, int queueTime) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);
        if (!enableCache)
            return;
        String key = StatsCache.getCacheKey(fullSqlQuery);
        CacheEntry entry = new CacheEntry(key, fullSqlQuery, result);
        entry.setExecTime(execTime);
        entry.setQueueTime(queueTime);
        entry.setProfile(fullSqlQuery.getDbId());
        entry.setFresh(true); // result is fresh from main DB
        storeEntry(entry);
    }

    @Override
    public void storeEntry(CacheEntry entry) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);
        // Every param in the `values(...)` derived table is explicitly CAST: HSQLDB types an
        // uninferable `?` there as VARCHAR(32768) and truncates any larger result/shadow/query
        // payload with "string data, right truncation", regardless of the real column width.
        // (Casting only some params breaks HSQLDB's type inference for the rest, so cast all.)
        // Counters (total_hits, session_hits) are intentionally excluded from the UPDATE clause:
        // they are managed exclusively by user-facing events (get() increments, save() on
        // invalid-entry repopulation, resetSessionHits() at promote). The update/promote cycle
        // must not overwrite them.
        String query = "merge into cache_entry as t using (values(cast(? as varchar(64)), cast(? as longvarchar), cast(? as longvarchar), cast(? as longvarchar), cast(? as timestamp), cast(? as timestamp), cast(? as int), cast(? as int), cast(? as boolean), cast(? as int), cast(? as int), cast(? as varchar(255)), cast(? as boolean))) as vals(key, result, shadow, query, created, updated, total, session, pinned, exectime, queuetime, profile, fresh) on t.key=vals.key when matched then update set t.result=vals.result, t.shadow=vals.shadow, t.query=vals.query, t.created=vals.updated, t.updated=vals.updated, t.pinned=vals.pinned, t.exectime=vals.exectime, t.queuetime=vals.queuetime, t.profile=vals.profile, t.fresh=vals.fresh when not matched then insert (key, result, shadow, query, created, updated, total_hits, session_hits, pinned, exectime, queuetime, profile, fresh) values (vals.key, vals.result, vals.shadow, vals.query, vals.created, vals.updated, vals.total, vals.session, vals.pinned, vals.exectime, vals.queuetime, vals.profile, vals.fresh);";

        log.debug("Storing entry " + entry);

        if (!enableCache) {
            throw new RuntimeException("Cache is not enabled!");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String resultJson = entry.getResult() == null ? null : objectMapper.writeValueAsString(entry.getResult());
        String shadowJson = entry.getShadowResult() == null ? null : objectMapper.writeValueAsString(entry.getShadowResult());
        String queryJson = entry.getQuery() == null ? null : objectMapper.writeValueAsString(entry.getQuery());

        int oversized = maxLength(resultJson, shadowJson, queryJson);
        if (oversized > MAX_CACHE_VALUE_CHARS) {
            log.warn("Skipping cache write for key {}: serialized payload is {} chars, over the {} char limit",
                    entry.getKey(), oversized, MAX_CACHE_VALUE_CHARS);
            return;
        }

        jdbcTemplate.update(query,
                entry.getKey(),
                resultJson,
                shadowJson,
                queryJson,
                Timestamp.from(entry.getCreated().toInstant()),
                Timestamp.from(entry.getUpdated().toInstant()),
                entry.getTotalHits(),
                entry.getSessionHits(),
                entry.isPinned(),
                entry.getExecTime(),
                entry.getQueueTime(),
                entry.getProfile(),
                entry.isFresh()
        );
    }

    private static int maxLength(String... values) {
        int max = 0;
        for (String value : values) {
            if (value != null && value.length() > max) {
                max = value.length();
            }
        }
        return max;
    }

    private CacheEntry mapCacheEntry(java.sql.ResultSet rs, int rowNum) {
        try {
            QueryWithParameters query = new ObjectMapper().readValue(rs.getString("query"), QueryWithParameters.class);
            String key = rs.getString("key");
            Result result = null;
            if (rs.getString("result") != null)
                result = new ObjectMapper().readValue(rs.getString("result"), Result.class);

            CacheEntry entry = new CacheEntry(key, query, result);

            if (rs.getTimestamp("created") != null)
                entry.setCreated(new Date(rs.getTimestamp("created").getTime()));
            if (rs.getTimestamp("updated") != null)
                entry.setUpdated(new Date(rs.getTimestamp("updated").getTime()));
            if (rs.getString("shadow") != null)
                entry.setShadowResult(new ObjectMapper().readValue(rs.getString("shadow"), Result.class));

            entry.setTotalHits(rs.getInt("total_hits"));
            entry.setSessionHits(rs.getInt("session_hits"));
            entry.setPinned(rs.getBoolean("pinned"));
            entry.setExecTime(rs.getInt("exectime"));
            entry.setQueueTime(rs.getInt("queuetime"));
            entry.setProfile(rs.getString("profile"));
            entry.setFresh(rs.getBoolean("fresh"));
            return entry;
        } catch (Exception e) {
            log.error("Error reading cache entry", e);
            return null;
        }
    }

    @Override
    public List<CacheEntry> getEntries(String profile) {
        DatasourceContext.setContext(CACHE_DB_NAME);

        if (!enableCache) {
            log.debug("Cache is not enabled. Returning empty list");
            return Collections.emptyList();
        }

        String sql = "select * from cache_entry where key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')";

        if (profile != null && !profile.isEmpty()) {
            sql += " and profile=?";
            return jdbcTemplate.query(sql, new Object[]{profile}, this::mapCacheEntry);
        }

        return jdbcTemplate.query(sql, this::mapCacheEntry);
    }

    @Override
    public void resetSessionHits(String profile) {
        DatasourceContext.setContext(CACHE_DB_NAME);
        if (profile != null && !profile.isEmpty()) {
            jdbcTemplate.update("update cache_entry set session_hits=0 where profile=?", profile);
        } else {
            jdbcTemplate.execute("update cache_entry set session_hits=0");
        }
    }

    @Override
    public boolean hasShadowEntries(String profile) {
        DatasourceContext.setContext(CACHE_DB_NAME);
        if (profile != null && !profile.isEmpty()) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from cache_entry where shadow is not null and profile=?",
                    new Object[]{profile}, Integer.class);
            return count != null && count > 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from cache_entry where shadow is not null",
                new Object[]{}, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void markAllStale(String profile) {
        DatasourceContext.setContext(CACHE_DB_NAME);
        if (profile != null && !profile.isEmpty()) {
            jdbcTemplate.update("update cache_entry set fresh=false where profile=?", profile);
        } else {
            jdbcTemplate.execute("update cache_entry set fresh=false");
        }
    }

    @Override
    public List<CacheEntry> getStaleEntries(String profile) {
        DatasourceContext.setContext(CACHE_DB_NAME);
        if (!enableCache) return Collections.emptyList();

        String baseSql = "select * from cache_entry where fresh=false" +
                " and key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')";

        if (profile != null && !profile.isEmpty()) {
            return jdbcTemplate.query(baseSql + " and profile=? order by total_hits desc",
                    new Object[]{profile}, this::mapCacheEntry);
        }
        return jdbcTemplate.query(baseSql + " order by total_hits desc", this::mapCacheEntry);
    }

    @Override
    public void trickleRefreshEntry(String key, Result result, int execTime, int queueTime) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);
        ObjectMapper objectMapper = new ObjectMapper();
        String resultJson = result == null ? null : objectMapper.writeValueAsString(result);
        if (resultJson != null && resultJson.length() > MAX_CACHE_VALUE_CHARS) {
            log.warn("Skipping trickle refresh for key {}: payload over limit", key);
            return;
        }
        jdbcTemplate.update(
                "update cache_entry set result=cast(? as longvarchar), fresh=true, exectime=?, queuetime=?, updated=now() where key=?",
                resultJson, execTime, queueTime, key);
    }

    @Override
    public void deleteEntry(String key) {
        DatasourceContext.setContext(CACHE_DB_NAME);

        if (!enableCache) {
            throw new RuntimeException("Cache is not enabled!");
        }

        jdbcTemplate.update("delete from cache_entry where key=?", key);
    }

    public Map<String, Object> stats() {
        DatasourceContext.setContext(CACHE_DB_NAME);
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("total", jdbcTemplate.queryForObject("select count(*) from cache_entry", new Object[] {}, Integer.class));
        stats.put("fresh", jdbcTemplate.queryForObject("select count(*) from cache_entry where fresh=true", new Object[] {}, Integer.class));
        stats.put("stale", jdbcTemplate.queryForObject("select count(*) from cache_entry where fresh=false", new Object[] {}, Integer.class));
        stats.put("with_shadow", jdbcTemplate.queryForObject("select count(*) from cache_entry where shadow is not null", new Object[] {}, Integer.class));
        stats.put("profiles", jdbcTemplate.query(
                "select profile," +
                "  count(*) as queries," +
                "  sum(case when fresh=true then 1 else 0 end) as fresh," +
                "  sum(case when fresh=false then 1 else 0 end) as stale," +
                "  sum(case when shadow is not null then 1 else 0 end) as with_shadow," +
                "  avg(exectime) as avg_exec_time," +
                "  avg(queuetime) as avg_queue_time" +
                " from cache_entry" +
                " where key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')" +
                " group by profile order by count(*) desc",
                (rs, rowNum) -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("profile", rs.getString("profile"));
                    map.put("queries", rs.getInt("queries"));
                    map.put("fresh", rs.getInt("fresh"));
                    map.put("stale", rs.getInt("stale"));
                    map.put("with_shadow", rs.getInt("with_shadow"));
                    map.put("avg_exec_time", rs.getInt("avg_exec_time"));
                    map.put("avg_queue_time", rs.getInt("avg_queue_time"));
                    return map;
                }));

        stats.put("total.top10", jdbcTemplate.query("select * from cache_entry where key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS') order by total_hits desc limit 10", (rs, rowNum) -> {
            CacheEntry entry = null;

            try {
                QueryWithParameters query = new ObjectMapper().readValue(rs.getString("query"), QueryWithParameters.class);
                String key = rs.getString("key");

                entry = new CacheEntry(key, query, null);

                if (rs.getTimestamp("created") != null)
                    entry.setCreated(new Date(rs.getTimestamp("created").getTime()));
                if (rs.getTimestamp("updated") != null)
                    entry.setUpdated(new Date(rs.getTimestamp("updated").getTime()));

                entry.setTotalHits(rs.getInt("total_hits"));
                entry.setSessionHits(rs.getInt("session_hits"));
                entry.setPinned(rs.getBoolean("pinned"));
                entry.setExecTime(rs.getInt("exectime"));
                entry.setProfile(rs.getString("profile"));
            } catch (IOException e) {
                log.error("Error reading entry", e);
            }

            return entry;
        }));
        stats.put("session.top10", jdbcTemplate.query("select * from cache_entry where key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS') order by session_hits desc limit 10", (rs, rowNum) -> {
            CacheEntry entry = null;

            try {
                QueryWithParameters query = new ObjectMapper().readValue(rs.getString("query"), QueryWithParameters.class);
                String key = rs.getString("key");

                entry = new CacheEntry(key, query, null);

                if (rs.getTimestamp("created") != null)
                    entry.setCreated(new Date(rs.getTimestamp("created").getTime()));
                if (rs.getTimestamp("updated") != null)
                    entry.setUpdated(new Date(rs.getTimestamp("updated").getTime()));

                entry.setTotalHits(rs.getInt("total_hits"));
                entry.setSessionHits(rs.getInt("session_hits"));
                entry.setPinned(rs.getBoolean("pinned"));
                entry.setExecTime(rs.getInt("exectime"));
                entry.setProfile(rs.getString("profile"));
            } catch (IOException e) {
                log.error("Error reading entry", e);
            }

            return entry;
        }));
        stats.put("total.heavy10", jdbcTemplate.query("select * from cache_entry where key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS') order by exectime desc limit 10", (rs, rowNum) -> {
            CacheEntry entry = null;

            try {
                QueryWithParameters query = new ObjectMapper().readValue(rs.getString("query"), QueryWithParameters.class);
                String key = rs.getString("key");

                entry = new CacheEntry(key, query, null);

                if (rs.getTimestamp("created") != null)
                    entry.setCreated(new Date(rs.getTimestamp("created").getTime()));
                if (rs.getTimestamp("updated") != null)
                    entry.setUpdated(new Date(rs.getTimestamp("updated").getTime()));

                entry.setTotalHits(rs.getInt("total_hits"));
                entry.setSessionHits(rs.getInt("session_hits"));
                entry.setPinned(rs.getBoolean("pinned"));
                entry.setExecTime(rs.getInt("exectime"));
                entry.setProfile(rs.getString("profile"));
            } catch (IOException e) {
                log.error("Error reading entry", e);
            }

            return entry;
        }));

        return stats;
    }
}
