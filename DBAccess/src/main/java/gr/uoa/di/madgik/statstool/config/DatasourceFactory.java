package gr.uoa.di.madgik.statstool.config;

import gr.uoa.di.madgik.statstool.repositories.datasource.StatsRoutingDatasource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Collections;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {"gr.uoa.di.madgik.statstool"})
public class DatasourceFactory {

    private static final String CONFIG_DB_ID = "config-db";
    private final Logger log = LogManager.getLogger(this.getClass());

    /**
     * Dedicated datasource for config tables. Never goes through the routing datasource
     * to avoid a circular dependency at startup. Must be in spring.datasources with id="config-db".
     */
    @Bean("configDataSource")
    public DataSource configDataSource(DataSourceProperties props) {
        DataSourceProperty p = props.getDataSources().stream()
            .filter(ds -> CONFIG_DB_ID.equals(ds.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Required datasource '" + CONFIG_DB_ID + "' not found in spring.datasources. " +
                "Add it to config/application.yml."));

        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("pool-config-db");
        ds.setJdbcUrl(p.getUrl());
        ds.setUsername(p.getUsername());
        ds.setPassword(p.getPassword());
        ds.setDriverClassName(p.getDriverClassName());
        ds.setMaximumPoolSize(p.getMaxPoolSize() > 0 ? p.getMaxPoolSize() : 5);
        ds.setConnectionTimeout(p.getConnTimeoutMs() > 0 ? p.getConnTimeoutMs() : 20_000);
        log.info("Created config-db datasource: {}", p.getUrl());
        return ds;
    }

    /**
     * Primary routing datasource. YAML datasources (excluding config-db) are registered
     * into the registry HERE — synchronously during bean creation — so they are available
     * before any @PostConstruct methods run on beans that use the routing datasource.
     * DB-sourced datasources are added later by DatasourceLoader.@PostConstruct.
     */
    @Bean("routingDataSource")
    @Primary
    public DataSource routingDataSource(DataSourceProperties props, DynamicDataSourceRegistry registry) {
        // Register all YAML datasources except config-db immediately.
        props.getDataSources().stream()
            .filter(p -> !CONFIG_DB_ID.equals(p.getId()))
            .forEach(p -> {
                registry.register(p);
                log.info("Registered YAML datasource '{}'", p.getId());
            });

        StatsRoutingDatasource ds = new StatsRoutingDatasource(registry);
        // AbstractRoutingDataSource requires a non-null target map; we pass empty because
        // routing bypasses the resolved map via our overridden determineTargetDataSource().
        ds.setTargetDataSources(Collections.emptyMap());
        ds.afterPropertiesSet();
        return ds;
    }
}
