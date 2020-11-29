package gr.uoa.di.madgik.statstool.config;

import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

@Configuration
public class DatasourceConfig {

    @Autowired
    private ExecutorService executorService;

    @Bean(name="publicDatasource")
    @ConfigurationProperties("spring.datasource.public")
    public DataSource publicDatasource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name="shadowDatasource")
    @ConfigurationProperties("spring.datasource.shadow")
    public DataSource shadowDatasource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name="publicStatsRepository")
    @Primary
    public StatsRepository publicStatsRepository() {
        return new StatsRepository(publicDatasource(), executorService);
    }

    @Bean(name="shadowStatsRepository")
    public StatsRepository shadowStatsRepository() {
        return new StatsRepository(shadowDatasource(), executorService);
    }
}
