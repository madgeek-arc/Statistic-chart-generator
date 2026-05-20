package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;

@Primary
@Repository
public class DbNlOptionsCache implements NlOptionsCache {

    private static final String DB = StatsDBRepository.CACHE_DB_NAME;

    private final JdbcTemplate jdbcTemplate;
    private final Logger log = LogManager.getLogger(this.getClass());

    public DbNlOptionsCache(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void init() {
        DatasourceContext.setContext(DB);
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS nl_options_cache (" +
            "  library          VARCHAR(255) NOT NULL," +
            "  canonical_desc   LONGVARCHAR  NOT NULL," +
            "  options_json     LONGVARCHAR  NOT NULL," +
            "  prompt_version   VARCHAR(64)  DEFAULT '1' NOT NULL," +
            "  created          TIMESTAMP    DEFAULT NOW() NOT NULL," +
            "  PRIMARY KEY (library, canonical_desc)" +
            ")"
        );
    }

    @Override
    public String get(String library, String canonicalDescription, String promptVersion) {
        DatasourceContext.setContext(DB);
        try {
            List<String> rows = jdbcTemplate.query(
                "SELECT options_json FROM nl_options_cache " +
                "WHERE library=? AND canonical_desc=? AND prompt_version=?",
                new Object[]{library, canonicalDescription, promptVersion},
                (rs, i) -> rs.getString("options_json")
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.error("Error reading nl_options_cache", e);
            return null;
        }
    }

    @Override
    public void put(String library, String canonicalDescription, String optionsJson, String promptVersion) {
        DatasourceContext.setContext(DB);
        try {
            jdbcTemplate.update(
                "MERGE INTO nl_options_cache AS t " +
                "USING (VALUES(?, ?, ?, ?)) AS v(library, canonical_desc, options_json, prompt_version) " +
                "ON t.library=v.library AND t.canonical_desc=v.canonical_desc " +
                "WHEN MATCHED THEN UPDATE SET t.options_json=v.options_json, t.prompt_version=v.prompt_version " +
                "WHEN NOT MATCHED THEN INSERT VALUES v.library, v.canonical_desc, v.options_json, " +
                "  v.prompt_version, NOW()",
                library, canonicalDescription, optionsJson, promptVersion
            );
        } catch (Exception e) {
            log.error("Error writing nl_options_cache", e);
        }
    }

    @Override
    public void evict(String library, String canonicalDescription) {
        DatasourceContext.setContext(DB);
        jdbcTemplate.update(
            "DELETE FROM nl_options_cache WHERE library=? AND canonical_desc=?",
            library, canonicalDescription
        );
    }

    @Override
    public void drop(String library) {
        DatasourceContext.setContext(DB);
        if (library != null && !library.isBlank()) {
            jdbcTemplate.update("DELETE FROM nl_options_cache WHERE library=?", library);
        } else {
            jdbcTemplate.execute("DELETE FROM nl_options_cache");
        }
    }
}
