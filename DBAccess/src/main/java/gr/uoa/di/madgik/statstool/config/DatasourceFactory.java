package gr.uoa.di.madgik.statstool.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
public class DatasourceFactory implements EnvironmentAware {

    public enum Mode {
        SHADOW("shadow"),
        PUBLIC("public");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public Environment env;

    @Bean
    public DataSource getDatasource(String profile, Mode mode) {
        String datasourceName = getDatasourceName(profile, mode);
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();

        dataSourceBuilder.driverClassName("spring.datasource." + datasourceName + ".driverClassName");
        dataSourceBuilder.url("spring.datasource." + datasourceName + ".jdbc-url");
        dataSourceBuilder.username("spring.datasource." + datasourceName + ".username");
        dataSourceBuilder.password("spring.datasource." + datasourceName + ".password");

        return dataSourceBuilder.build();
    };

    @Override
    public void setEnvironment(Environment environment) {

    }

    private String getDatasourceName(String profileName, Mode mode) {
        return profileName + "." + mode.value;
    }
}
