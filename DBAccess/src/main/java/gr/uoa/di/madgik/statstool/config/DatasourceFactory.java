package gr.uoa.di.madgik.statstool.config;

import gr.uoa.di.madgik.statstool.repositories.datasource.StatsRoutingDatasource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {"gr.uoa.di.madgik.statstool"})
public class DatasourceFactory {

    @Bean(name = "dataSources")
    @Primary
    public Map<Object, Object> getDataSources(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.getDataSources()
                .stream()
                .map(this::createDataSource)
                .collect(Collectors.toMap(IdDataSource::getId, IdDataSource::getDataSource));
    }

    @Bean(name = "routingDataSource")
    @DependsOn("dataSources")
    public DataSource dataSource(Map<Object, Object> dataSources) {
        AbstractRoutingDataSource routingDataSource = new StatsRoutingDatasource();
        routingDataSource.setTargetDataSources(dataSources);
        routingDataSource.setDefaultTargetDataSource(dataSources.get("db1"));
        routingDataSource.afterPropertiesSet();

        return routingDataSource;
    }

    private IdDataSource createDataSource(DataSourceProperty dataSourceProperty) {
        DataSource dataSource = DataSourceBuilder.create()
                .url(dataSourceProperty.getUrl())
                .username(dataSourceProperty.getUsername())
                .password(dataSourceProperty.getPassword())
                .driverClassName(dataSourceProperty.getDriverClassName())
                .build();

        return new IdDataSource(dataSourceProperty.getId(), dataSource);
    }

    private static class IdDataSource {
        private Object id;
        private Object dataSource;

        public IdDataSource(Object id, Object dataSource) {
            this.id = id;
            this.dataSource = dataSource;
        }

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

        public Object getDataSource() {
            return dataSource;
        }

        public void setDataSource(Object dataSource) {
            this.dataSource = dataSource;
        }
    }
}