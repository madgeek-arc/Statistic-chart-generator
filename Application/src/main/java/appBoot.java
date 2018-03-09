
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = { "JsonChartRepresentation","RestControllers"} )
@SpringBootApplication
public class appBoot {

    public static void main(String[] args) {

        SpringApplication.run(appBoot.class, args);
    }
}