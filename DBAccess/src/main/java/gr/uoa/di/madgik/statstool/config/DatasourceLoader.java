package gr.uoa.di.madgik.statstool.config;

import gr.uoa.di.madgik.statstool.repositories.ConfigRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Populates DynamicDataSourceRegistry at startup:
 *  1. YAML-defined datasources (excluding config-db which has its own bean).
 *  2. DB-defined datasources from statstool_datasource.
 *
 * Runs after ConfigRepository is ready (declared as @Autowired dependency).
 */
@Component
public class DatasourceLoader {

    private static final String CONFIG_DB_ID = "config-db";

    private final Logger log = LogManager.getLogger(this.getClass());

    @Autowired private DataSourceProperties yamlProps;
    @Autowired private DynamicDataSourceRegistry registry;
    @Autowired private ConfigRepository configRepository;

    @PostConstruct
    public void load() {
        // Register YAML datasources (skip config-db — it has a dedicated bean).
        yamlProps.getDataSources().stream()
            .filter(p -> !CONFIG_DB_ID.equals(p.getId()))
            .forEach(p -> {
                registry.register(p);
                log.info("Loaded YAML datasource '{}'", p.getId());
            });

        // Register DB-defined datasources (may override YAML ones by same id).
        configRepository.loadDatasources().forEach(p -> {
            registry.register(p);
            log.info("Loaded DB datasource '{}'", p.getId());
        });
    }
}
