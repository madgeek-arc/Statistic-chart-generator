package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.repositories.NlCachedEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;

@Primary
@Repository
public class DbNlSqlCache implements NlSqlCache {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger log = LogManager.getLogger(this.getClass());

    public DbNlSqlCache(@Qualifier("cacheDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS nl_sql_cache (" +
            "  profile            VARCHAR(255) NOT NULL," +
            "  canonical_nl       TEXT         NOT NULL," +
            "  sql_query          TEXT         NOT NULL," +
            "  parameters         TEXT," +
            "  db_id              VARCHAR(255)," +
            "  schema_fingerprint VARCHAR(64)  DEFAULT '' NOT NULL," +
            "  description        TEXT         DEFAULT '' NOT NULL," +
            "  created            TIMESTAMP    DEFAULT NOW() NOT NULL," +
            "  PRIMARY KEY (profile, canonical_nl)" +
            ")"
        );
    }

    @Override
    public NlCachedEntry get(String profile, String canonicalNl, String schemaFingerprint) {
        try {
            List<NlCachedEntry> rows = jdbcTemplate.query(
                "SELECT sql_query, parameters, db_id, description FROM nl_sql_cache " +
                "WHERE profile=? AND canonical_nl=? AND schema_fingerprint=?",
                new Object[]{profile, canonicalNl, schemaFingerprint},
                (rs, i) -> {
                    try {
                        List<Object> params = rs.getString("parameters") != null
                            ? mapper.readValue(rs.getString("parameters"), new TypeReference<List<Object>>() {})
                            : List.of();
                        QueryWithParameters qwp = new QueryWithParameters(
                            rs.getString("sql_query"), params, rs.getString("db_id"));
                        return new NlCachedEntry(qwp, rs.getString("description"));
                    } catch (Exception e) {
                        log.error("Error deserialising nl_sql_cache entry", e);
                        return null;
                    }
                }
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.error("Error reading nl_sql_cache", e);
            return null;
        }
    }

    @Override
    public void put(String profile, String canonicalNl, NlCachedEntry entry, String schemaFingerprint) {
        try {
            QueryWithParameters qwp = entry.qwp();
            String paramsJson = mapper.writeValueAsString(qwp.getParameters());
            String description = entry.description() != null ? entry.description() : "";
            jdbcTemplate.update(
                "INSERT INTO nl_sql_cache " +
                "  (profile, canonical_nl, sql_query, parameters, db_id, schema_fingerprint, description, created) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (profile, canonical_nl) DO UPDATE SET " +
                "  sql_query=EXCLUDED.sql_query, parameters=EXCLUDED.parameters," +
                "  db_id=EXCLUDED.db_id, schema_fingerprint=EXCLUDED.schema_fingerprint," +
                "  description=EXCLUDED.description",
                profile, canonicalNl, qwp.getQuery(), paramsJson, qwp.getDbId(), schemaFingerprint, description
            );
        } catch (Exception e) {
            log.error("Error writing nl_sql_cache", e);
        }
    }

    @Override
    public void evict(String profile, String canonicalNl) {
        jdbcTemplate.update("DELETE FROM nl_sql_cache WHERE profile=? AND canonical_nl=?", profile, canonicalNl);
    }

    @Override
    public void drop(String profile) {
        if (profile != null && !profile.isBlank()) {
            jdbcTemplate.update("DELETE FROM nl_sql_cache WHERE profile=?", profile);
        } else {
            jdbcTemplate.execute("DELETE FROM nl_sql_cache");
        }
    }
}
