package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.config.DataSourceProperty;
import gr.uoa.di.madgik.statstool.mapping.domain.Mapping;
import gr.uoa.di.madgik.statstool.mapping.domain.MappingProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes dynamic configuration from the config-db datasource.
 * Uses its own JdbcTemplate so it never routes through StatsRoutingDatasource
 * — this avoids a circular dependency at startup.
 */
@Repository
public class ConfigRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger log = LogManager.getLogger(this.getClass());

    public ConfigRepository(@Qualifier("configDataSource") DataSource configDataSource) {
        this.jdbc = new JdbcTemplate(configDataSource);
    }

    // -------------------------------------------------------------------------
    // Datasources
    // -------------------------------------------------------------------------

    public List<DataSourceProperty> loadDatasources() {
        return jdbc.query(
            "SELECT id, url, driver_class, username, password, max_pool_size, conn_timeout_ms, connection_init_sql " +
            "FROM statstool_datasource WHERE enabled = TRUE",
            (rs, row) -> {
                DataSourceProperty p = new DataSourceProperty();
                p.setId(rs.getString("id"));
                p.setUrl(rs.getString("url"));
                p.setDriverClassName(rs.getString("driver_class"));
                p.setUsername(rs.getString("username"));
                p.setPassword(rs.getString("password"));
                p.setMaxPoolSize(rs.getInt("max_pool_size"));
                p.setConnTimeoutMs(rs.getInt("conn_timeout_ms"));
                p.setConnectionInitSql(rs.getString("connection_init_sql"));
                return p;
            });
    }

    public void saveDatasource(DataSourceProperty p) {
        jdbc.update(
            "INSERT INTO statstool_datasource " +
            "(id, url, driver_class, username, password, max_pool_size, conn_timeout_ms, connection_init_sql, enabled, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "  url = EXCLUDED.url, driver_class = EXCLUDED.driver_class, " +
            "  username = EXCLUDED.username, password = EXCLUDED.password, " +
            "  max_pool_size = EXCLUDED.max_pool_size, conn_timeout_ms = EXCLUDED.conn_timeout_ms, " +
            "  connection_init_sql = EXCLUDED.connection_init_sql, " +
            "  enabled = TRUE, updated_at = NOW()",
            p.getId(), p.getUrl(), p.getDriverClassName(),
            p.getUsername(), p.getPassword(),
            p.getMaxPoolSize(), p.getConnTimeoutMs(), p.getConnectionInitSql());
    }

    public void deleteDatasource(String id) {
        jdbc.update("DELETE FROM statstool_datasource WHERE id = ?", id);
    }

    // -------------------------------------------------------------------------
    // Mapping profiles
    // -------------------------------------------------------------------------

    public List<MappingProfile> loadProfiles() {
        return jdbc.query(
            "SELECT name, description, usage_hint, shareholders, complexity, " +
            "is_primary, is_hidden, mapping_content FROM statstool_mapping_profile",
            (rs, row) -> {
                MappingProfile p = new MappingProfile();
                p.setName(rs.getString("name"));
                p.setDescription(rs.getString("description"));
                p.setUsage(rs.getString("usage_hint"));
                try {
                    String shareholders = rs.getString("shareholders");
                    if (shareholders != null) {
                        p.setShareholders(objectMapper.readValue(shareholders,
                            new TypeReference<List<String>>() {}));
                    }
                    String mappingJson = rs.getString("mapping_content");
                    p.setContent(objectMapper.readValue(mappingJson, Mapping.class));
                } catch (Exception e) {
                    log.error("Failed to deserialize profile '{}'", rs.getString("name"), e);
                }
                p.setComplexity(rs.getInt("complexity"));
                p.setPrimary(rs.getBoolean("is_primary"));
                p.setHidden(rs.getBoolean("is_hidden"));
                return p;
            });
    }

    public void saveProfile(MappingProfile p) {
        try {
            String shareholders = p.getShareholders() != null
                ? objectMapper.writeValueAsString(p.getShareholders()) : "[]";
            String mappingJson = objectMapper.writeValueAsString(p.getContent());
            jdbc.update(
                "INSERT INTO statstool_mapping_profile " +
                "(name, description, usage_hint, shareholders, complexity, is_primary, is_hidden, mapping_content, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (name) DO UPDATE SET " +
                "  description = EXCLUDED.description, usage_hint = EXCLUDED.usage_hint, " +
                "  shareholders = EXCLUDED.shareholders, complexity = EXCLUDED.complexity, " +
                "  is_primary = EXCLUDED.is_primary, is_hidden = EXCLUDED.is_hidden, " +
                "  mapping_content = EXCLUDED.mapping_content, updated_at = NOW()",
                p.getName(), p.getDescription(), p.getUsage(),
                shareholders, p.getComplexity(), p.isPrimary(), p.isHidden(),
                mappingJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save profile '" + p.getName() + "'", e);
        }
    }

    public void deleteProfile(String name) {
        jdbc.update("DELETE FROM statstool_mapping_profile WHERE name = ?", name);
    }

    // -------------------------------------------------------------------------
    // Named queries
    // -------------------------------------------------------------------------

    /** Returns all named queries as name → query text map (preserves insertion order). */
    public Map<String, String> loadNamedQueries() {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query(
            "SELECT name, query FROM statstool_named_query ORDER BY name",
            (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> result.put(rs.getString("name"), rs.getString("query")));
        return result;
    }

    public void saveNamedQuery(String name, String query, String description) {
        jdbc.update(
            "INSERT INTO statstool_named_query (name, query, description, updated_at) " +
            "VALUES (?, ?, ?, NOW()) " +
            "ON CONFLICT (name) DO UPDATE SET " +
            "  query = EXCLUDED.query, description = EXCLUDED.description, updated_at = NOW()",
            name, query, description);
    }

    public void deleteNamedQuery(String name) {
        jdbc.update("DELETE FROM statstool_named_query WHERE name = ?", name);
    }
}
