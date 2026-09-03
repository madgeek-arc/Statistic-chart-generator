package gr.uoa.di.madgik.ChartDataFormatter.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Bound outbound calls (currently only the best-effort TinyURL shortener) so a slow
        // or hung upstream cannot pin a Tomcat worker thread indefinitely.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return new RestTemplate(factory);
    }
}
