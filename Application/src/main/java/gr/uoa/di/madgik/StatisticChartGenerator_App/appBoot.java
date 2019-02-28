package gr.uoa.di.madgik.StatisticChartGenerator_App;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

import java.util.Properties;

@ComponentScan(basePackages = {"gr/uoa/di/madgik/ChartDataFormatter", "gr/uoa/di/madgik/statstool"})
@SpringBootApplication
public class appBoot extends SpringBootServletInitializer {

    public static void main(String[] args) {

        SpringApplication.run(appBoot.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder springApplicationBuilder) {
        return springApplicationBuilder
                .sources(appBoot.class)
                .properties(getProperties());
    }

    static Properties getProperties() {
        Properties props = new Properties();
        props.put("spring.config.location", "classpath:statsConfig/");
        return props;
    }
}