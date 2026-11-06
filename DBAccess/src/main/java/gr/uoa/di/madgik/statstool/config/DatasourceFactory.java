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
     * Dedicated datasource for config tables (statstool_datasource, statstool_mapping_profile,
     * statstool_named_query). Never goes through the routing datasource to avoid a circular
     * dependency at startup. Must be declared in spring.datasources with id="config-db".
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
     * Primary routing datasource. Delegates pool lookup to DynamicDataSourceRegistry,
     * which is populated by DatasourceLoader after Spring context is fully wired.
     */
    @Bean("routingDataSource")
    @Primary
    public DataSource routingDataSource(DynamicDataSourceRegistry registry) {
        StatsRoutingDatasource ds = new StatsRoutingDatasource(registry);
        // AbstractRoutingDataSource requires a non-null target map; we pass empty
        // because actual routing bypasses the resolved map via determineTargetDataSource().
        ds.setTargetDataSources(Collections.emptyMap());
        ds.afterPropertiesSet();
        return ds;
    }
}
