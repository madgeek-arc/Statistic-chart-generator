package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private static final int MAX_CACHE_VALUE_CHARS = 15 * 1024 * 1024;

    @Value("${statstool.cache.enabled:true}")
    private boolean enableCache;

    private final Logger log = LogManager.getLogger(this.getClass());

    private final JdbcTemplate jdbcTemplate;

    public StatsDBRepository(@Qualifier("cacheDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void postInit() {
        log.debug("Creating cache table");
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS cache_entry (" +
            "  key          VARCHAR(64)  NOT NULL," +
            "  result       TEXT         NOT NULL," +
            "  shadow       TEXT," +
            "  query        TEXT         NOT NULL," +
            "  created      TIMESTAMP    DEFAULT NOW() NOT NULL," +
            "  updated      TIMESTAMP    DEFAULT NOW() NOT NULL," +
            "  total_hits   INT          DEFAULT 0 NOT NULL," +
            "  session_hits INT          DEFAULT 0 NOT NULL," +
            "  pinned       BOOLEAN      DEFAULT FALSE NOT NULL," +
            "  exectime     INT          DEFAULT 0 NOT NULL," +
            "  queuetime    INT          DEFAULT 0 NOT NULL," +
            "  profile      VARCHAR(255) NOT NULL," +
            "  fresh        BOOLEAN      DEFAULT FALSE NOT NULL," +
            "  PRIMARY KEY (key)" +
            ")"
        );
        log.info("cache_entry table ready");
    }

    @Override
    public boolean isEnabled() {
        return enableCache;
    }

    @Override
    public Result get(String key) throws Exception {
        if (!enableCache)
            return null;

        int rows = jdbcTemplate.update(
                "UPDATE cache_entry SET total_hits=total_hits+1, session_hits=session_hits+1 WHERE key=?", key);

        if (rows == 0)
            return null;

        return jdbcTemplate.queryForObject("SELECT result, fresh FROM cache_entry WHERE key=?", new Object[]{key}, (resultSet, i) -> {
            if (!resultSet.getBoolean("fresh"))
                return null;
            try {
                return new ObjectMapper().readValue(resultSet.getString("result"), Result.class);
            } catch (IOException e) {
                log.error("Error getting entry with key " + key, e);
            }
            throw new RuntimeException("Error deserializing cached result for key " + key);
        });
    }

    public void dropCache(String profile) throws Exception {
        String sql = "DELETE FROM cache_entry";
        if (profile != null && !profile.isEmpty()) {
            jdbcTemplate.update(sql + " WHERE profile=?", profile);
        } else {
            jdbcTemplate.execute(sql);
        }
    }

    @Override
    public void save(QueryWithParameters fullSqlQuery, Result result, int execTime, int queueTime) throws Exception {
        if (!enableCache)
            return;
        String key = StatsCache.getCacheKey(fullSqlQuery);
        CacheEntry entry = new CacheEntry(key, fullSqlQuery, result);
        entry.setExecTime(execTime);
        entry.setQueueTime(queueTime);
        entry.setProfile(fullSqlQuery.getDbId());
        entry.setFresh(true);
        storeEntry(entry);
    }

    @Override
    public void storeEntry(CacheEntry entry) throws Exception {
        if (!enableCache) {
            throw new RuntimeException("Cache is not enabled!");
        }

        log.debug("Storing entry " + entry);

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

        // Counters (total_hits, session_hits) excluded from UPDATE — managed by get() / resetSessionHits().
        jdbcTemplate.update(
            "INSERT INTO cache_entry " +
            "  (key, result, shadow, query, created, updated, total_hits, session_hits, pinned, exectime, queuetime, profile, fresh) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (key) DO UPDATE SET " +
            "  result=EXCLUDED.result, shadow=EXCLUDED.shadow, query=EXCLUDED.query," +
            "  created=EXCLUDED.updated, updated=EXCLUDED.updated, pinned=EXCLUDED.pinned," +
            "  exectime=EXCLUDED.exectime, queuetime=EXCLUDED.queuetime," +
            "  profile=EXCLUDED.profile, fresh=EXCLUDED.fresh",
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
            if (value != null && value.length() > max) max = value.length();
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
        if (!enableCache) {
            log.debug("Cache is not enabled. Returning empty list");
            return Collections.emptyList();
        }

        String sql = "SELECT * FROM cache_entry WHERE key NOT IN ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')";

        if (profile != null && !profile.isEmpty()) {
            sql += " AND profile=?";
            return jdbcTemplate.query(sql, new Object[]{profile}, this::mapCacheEntry);
        }

        return jdbcTemplate.query(sql, this::mapCacheEntry);
    }

    @Override
    public void resetSessionHits(String profile) {
        if (profile != null && !profile.isEmpty()) {
            jdbcTemplate.update("UPDATE cache_entry SET session_hits=0 WHERE profile=?", profile);
        } else {
            jdbcTemplate.execute("UPDATE cache_entry SET session_hits=0");
        }
    }

    @Override
    public boolean hasShadowEntries(String profile) {
        if (profile != null && !profile.isEmpty()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM cache_entry WHERE shadow IS NOT NULL AND profile=?",
                    new Object[]{profile}, Integer.class);
            return count != null && count > 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cache_entry WHERE shadow IS NOT NULL",
                new Object[]{}, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<String> getProfilesWithShadows() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT profile FROM cache_entry WHERE shadow IS NOT NULL",
                String.class);
    }

    @Override
    public void markAllStale(String profile) {
        if (profile != null && !profile.isEmpty()) {
            jdbcTemplate.update("UPDATE cache_entry SET fresh=false WHERE profile=?", profile);
        } else {
            jdbcTemplate.execute("UPDATE cache_entry SET fresh=false");
        }
    }

    @Override
    public List<CacheEntry> getStaleEntries(String profile) {
        if (!enableCache) return Collections.emptyList();

        String baseSql = "SELECT * FROM cache_entry WHERE fresh=false" +
                " AND key NOT IN ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')";

        if (profile != null && !profile.isEmpty()) {
            return jdbcTemplate.query(baseSql + " AND profile=? ORDER BY total_hits DESC",
                    new Object[]{profile}, this::mapCacheEntry);
        }
        return jdbcTemplate.query(baseSql + " ORDER BY total_hits DESC", this::mapCacheEntry);
    }

    @Override
    public void trickleRefreshEntry(String key, Result result, int execTime, int queueTime) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String resultJson = result == null ? null : objectMapper.writeValueAsString(result);
        if (resultJson != null && resultJson.length() > MAX_CACHE_VALUE_CHARS) {
            log.warn("Skipping trickle refresh for key {}: payload over limit", key);
            return;
        }
        jdbcTemplate.update(
                "UPDATE cache_entry SET result=?, fresh=true, exectime=?, queuetime=?, updated=NOW() WHERE key=?",
                resultJson, execTime, queueTime, key);
    }

    @Override
    public void deleteEntry(String key) {
        if (!enableCache) {
            throw new RuntimeException("Cache is not enabled!");
        }
        jdbcTemplate.update("DELETE FROM cache_entry WHERE key=?", key);
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("total", jdbcTemplate.queryForObject("SELECT count(*) FROM cache_entry", new Object[] {}, Integer.class));
        stats.put("fresh", jdbcTemplate.queryForObject("SELECT count(*) FROM cache_entry WHERE fresh=true", new Object[] {}, Integer.class));
        stats.put("stale", jdbcTemplate.queryForObject("SELECT count(*) FROM cache_entry WHERE fresh=false", new Object[] {}, Integer.class));
        stats.put("with_shadow", jdbcTemplate.queryForObject("SELECT count(*) FROM cache_entry WHERE shadow IS NOT NULL", new Object[] {}, Integer.class));
        stats.put("profiles", jdbcTemplate.query(
                "SELECT profile," +
                "  count(*) AS queries," +
                "  sum(CASE WHEN fresh=true THEN 1 ELSE 0 END) AS fresh," +
                "  sum(CASE WHEN fresh=false THEN 1 ELSE 0 END) AS stale," +
                "  sum(CASE WHEN shadow IS NOT NULL THEN 1 ELSE 0 END) AS with_shadow," +
                "  avg(exectime) AS avg_exec_time," +
                "  avg(queuetime) AS avg_queue_time" +
                " FROM cache_entry" +
                " WHERE key NOT IN ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')" +
                " GROUP BY profile ORDER BY count(*) DESC",
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

        stats.put("total.top10", jdbcTemplate.query(
            "SELECT * FROM cache_entry WHERE key NOT IN ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS') ORDER BY total_hits DESC LIMIT 10",
            (rs, rowNum) -> statsEntry(rs)));
        stats.put("session.top10", jdbcTemplate.query(
            "SELECT * FROM cache_entry WHERE key NOT IN ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS') ORDER BY session_hits DESC LIMIT 10",
            (rs, rowNum) -> statsEntry(rs)));
        stats.put("total.heavy10", jdbcTemplate.query(
            "SELECT * FROM cache_entry WHERE key NOT IN ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS') ORDER BY exectime DESC LIMIT 10",
            (rs, rowNum) -> statsEntry(rs)));

        return stats;
    }

    private CacheEntry statsEntry(java.sql.ResultSet rs) {
        try {
            QueryWithParameters query = new ObjectMapper().readValue(rs.getString("query"), QueryWithParameters.class);
            CacheEntry entry = new CacheEntry(rs.getString("key"), query, null);
            if (rs.getTimestamp("created") != null) entry.setCreated(new Date(rs.getTimestamp("created").getTime()));
            if (rs.getTimestamp("updated") != null) entry.setUpdated(new Date(rs.getTimestamp("updated").getTime()));
            entry.setTotalHits(rs.getInt("total_hits"));
            entry.setSessionHits(rs.getInt("session_hits"));
            entry.setPinned(rs.getBoolean("pinned"));
            entry.setExecTime(rs.getInt("exectime"));
            entry.setProfile(rs.getString("profile"));
            return entry;
        } catch (Exception e) {
            log.error("Error reading stats entry", e);
            return null;
        }
    }
}
