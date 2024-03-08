package gr.uoa.di.madgik.StatisticChartGenerator_App;

import gr.uoa.di.madgik.ChartDataFormatter.Utility.YamlPropertySourceFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.PropertySource;

import java.util.Properties;

@SpringBootApplication(scanBasePackages = {"gr/uoa/di/madgik/ChartDataFormatter", "gr/uoa/di/madgik/statstool"})
@PropertySource(value = "classpath:application.yml", factory = YamlPropertySourceFactory.class)
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
