package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository
@ConditionalOnProperty(
        value = "statstool.cache.storage",
        havingValue = "db")
public class StatsDBRepository implements StatsCache {

    public static final String CACHE_DB_NAME = "cache";

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
                        "key varchar(10000) not null," +
                        "result longvarchar not null, " +
                        "shadow longvarchar, " +
                        "query varchar(10000) not null," +
                        "created timestamp default now() not null, " +
                        "updated timestamp default now() not null, " +
                        "total_hits int default 0 not null," +
                        "session_hits int default 0 not null," +
                        "pinned boolean default false not null)");
    }

    @Override
    public boolean exists(String key) {
        if (!enableCache) {
            log.warn("Calling exists method while cache is not enabled.");
            throw new RuntimeException("Cache is not enabled!");
        }

        DatasourceContext.setContext(CACHE_DB_NAME);

        log.debug("Checking if entry with key " + key + " exists");

        return jdbcTemplate.queryForObject("select count(*) from cache_entry where key=?",new Object[] {key}, Integer.class) == 1;
    }

    @Override
    public Result get(String key) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);

        if (!enableCache)
            throw new RuntimeException("Cache is not enabled!");

        if (!exists(key))
            throw new Exception("Key " + key + " does not exist");

        jdbcTemplate.update("update cache_entry set total_hits=total_hits+1, session_hits=session_hits+1 where key=?", key);

        return jdbcTemplate.queryForObject("select result from cache_entry where key=?", new Object[]{key}, (resultSet, i) -> {
            try {
                return new ObjectMapper().readValue(resultSet.getString("result"), Result.class);
            } catch (IOException e) {
                log.error("Error getting entry with key " + key, e);
            }

            throw new RuntimeException("Something went baad");
        });
    }

    public void dropCache() throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);

        jdbcTemplate.execute("delete from cache_entry");
    }

    @Override
    public String save(QueryWithParameters fullSqlQuery, Result result) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);
        try {
            String key = StatsCache.getCacheKey(fullSqlQuery);

            if (!enableCache)
                throw new RuntimeException("Cache is not enabled!");
            else {
                CacheEntry entry = new CacheEntry(key, fullSqlQuery, result);

                storeEntry(entry);
            }

            return key;
        } catch (Exception e) {
            throw new RedisException(e);
        }
    }

    @Override
    public void storeEntry(CacheEntry entry) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);
//	String query = "insert into cache_entry (key, result, shadow, query, created, updated, total_hits, session_hits, pinned) values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
	String query = "merge into cache_entry as t using (values(?, ?, ?, ?, ?, ?, ?, ?, ?)) as vals(key, result, shadow, query, created, updated, total, session, pinned) on t.key=vals.key when matched then update set t.result=vals.result, t.shadow=vals.shadow, t.query=vals.query, t.created=vals.updated, t.updated=vals.updated, t.total_hits=vals.total, t.session_hits=vals.session, t.pinned=vals.pinned when not matched then insert values vals.key, vals.result, vals.shadow, vals.query, vals.created, vals.updated, vals.total, vals.session, vals.pinned;";

        log.debug("Storing entry " + entry);

        if (!enableCache) {
            throw new RuntimeException("Cache is not enabled!");
        }
        jdbcTemplate.update(query,
                entry.getKey(),
                new ObjectMapper().writeValueAsString(entry.getResult()),
                new ObjectMapper().writeValueAsString(entry.getShadowResult()),
                new ObjectMapper().writeValueAsString(entry.getQuery()),
                Timestamp.from(entry.getCreated().toInstant()),
                Timestamp.from(entry.getUpdated().toInstant()),
                entry.getTotalHits(),
                entry.getSessionHits(),
                entry.isPinned()
        );
    }

    @Override
    public List<CacheEntry> getEntries() {
        DatasourceContext.setContext(CACHE_DB_NAME);

        if (!enableCache) {
            log.debug("Cache is not enabled. Returning empty list");

            return Collections.emptyList();
        }

        return jdbcTemplate.query("select * from cache_entry where key not in ('SHADOW_STATS_NUMBERS', 'STATS_NUMBERS')", (rs, rowNum) -> {
            CacheEntry entry = null;

            try {
                QueryWithParameters query = new ObjectMapper().readValue(rs.getString("query"), QueryWithParameters.class);
                String key = rs.getString("key");
                Result result = new ObjectMapper().readValue(rs.getString("result"), Result.class);

                entry = new CacheEntry(key, query, result);

                if (rs.getTimestamp("created") != null)
                    entry.setCreated(new Date(rs.getTimestamp("created").getTime()));
                if (rs.getTimestamp("updated") != null)
                    entry.setUpdated(new Date(rs.getTimestamp("updated").getTime()));
                if (rs.getString("shadow") != null)
                    entry.setShadowResult(new ObjectMapper().readValue(rs.getString("shadow"), Result.class));

                entry.setTotalHits(rs.getInt("total_hits"));
                entry.setSessionHits(rs.getInt("session_hits"));
                entry.setPinned(rs.getBoolean("pinned"));
            } catch (IOException e) {
                log.error("Error reading entry", e);
            }

            return entry;
        });
    }

    @Override
    public void deleteEntry(String key) {
        DatasourceContext.setContext(CACHE_DB_NAME);

        if (!enableCache) {
            throw new RuntimeException("Cache is not enabled!");
        }

        jdbcTemplate.update("delete from cache_entry where key=?", key);
    }
}
