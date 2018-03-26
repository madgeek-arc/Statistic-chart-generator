package gr.uoa.di.madgik.StatisticChartGenerator_App;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"gr/uoa/di/madgik/ChartDataFormatter"} )
@SpringBootApplication
public class appBoot {

    public static void main(String[] args) {

        SpringApplication.run(appBoot.class, args);
    }
}