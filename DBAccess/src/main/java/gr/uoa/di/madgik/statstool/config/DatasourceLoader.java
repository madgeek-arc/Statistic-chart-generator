package gr.uoa.di.madgik.statstool.config;

import gr.uoa.di.madgik.statstool.repositories.ConfigRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Loads DB-defined datasources into DynamicDataSourceRegistry at startup.
 * YAML datasources are registered earlier (synchronously in DatasourceFactory.routingDataSource)
 * so they are available before any @PostConstruct methods on routing-datasource consumers.
 * This loader only adds datasources stored in statstool_datasource, which may override
 * or supplement the YAML-defined ones.
 */
@Component
public class DatasourceLoader {

    private final Logger log = LogManager.getLogger(this.getClass());

    @Autowired private DynamicDataSourceRegistry registry;
    @Autowired private ConfigRepository configRepository;

    @PostConstruct
    public void load() {
        configRepository.loadDatasources().forEach(p -> {
            registry.register(p);
            log.info("Loaded DB datasource '{}'", p.getId());
        });
    }
}
