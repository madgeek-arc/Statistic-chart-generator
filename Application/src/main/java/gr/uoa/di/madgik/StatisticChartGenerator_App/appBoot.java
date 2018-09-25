package gr.uoa.di.madgik.StatisticChartGenerator_App;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"gr/uoa/di/madgik/ChartDataFormatter", "gr/uoa/di/madgik/statstool"} )
@SpringBootApplication
//public class appBoot {
public class appBoot extends SpringBootServletInitializer {

    public static void main(String[] args) {

        SpringApplication.run(appBoot.class, args);
    }
}