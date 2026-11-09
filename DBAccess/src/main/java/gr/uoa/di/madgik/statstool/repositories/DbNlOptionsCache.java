package gr.uoa.di.madgik.statstool.repositories;

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
public class DbNlOptionsCache implements NlOptionsCache {

    private final JdbcTemplate jdbcTemplate;
    private final Logger log = LogManager.getLogger(this.getClass());

    public DbNlOptionsCache(@Qualifier("cacheDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS nl_options_cache (" +
            "  library          VARCHAR(255) NOT NULL," +
            "  canonical_desc   TEXT         NOT NULL," +
            "  options_json     TEXT         NOT NULL," +
            "  prompt_version   VARCHAR(64)  DEFAULT '1' NOT NULL," +
            "  created          TIMESTAMP    DEFAULT NOW() NOT NULL," +
            "  PRIMARY KEY (library, canonical_desc)" +
            ")"
        );
    }

    @Override
    public String get(String library, String canonicalDescription, String promptVersion) {
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
        try {
            jdbcTemplate.update(
                "INSERT INTO nl_options_cache (library, canonical_desc, options_json, prompt_version, created) " +
                "VALUES (?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (library, canonical_desc) DO UPDATE SET " +
                "  options_json=EXCLUDED.options_json, prompt_version=EXCLUDED.prompt_version",
                library, canonicalDescription, optionsJson, promptVersion
            );
        } catch (Exception e) {
            log.error("Error writing nl_options_cache", e);
        }
    }

    @Override
    public void evict(String library, String canonicalDescription) {
        jdbcTemplate.update(
            "DELETE FROM nl_options_cache WHERE library=? AND canonical_desc=?",
            library, canonicalDescription
        );
    }

    @Override
    public void drop(String library) {
        if (library != null && !library.isBlank()) {
            jdbcTemplate.update("DELETE FROM nl_options_cache WHERE library=?", library);
        } else {
            jdbcTemplate.execute("DELETE FROM nl_options_cache");
        }
    }
}
