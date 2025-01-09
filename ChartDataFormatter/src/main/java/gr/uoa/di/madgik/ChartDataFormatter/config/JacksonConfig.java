package gr.uoa.di.madgik.ChartDataFormatter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Disable scientific notation for floating point numbers
        mapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);

        // Register custom serializers if needed
        SimpleModule module = new SimpleModule();
        module.addSerializer(Double.class, new ToStringSerializer());
        module.addSerializer(Float.class, new ToStringSerializer());
        mapper.registerModule(module);

        return mapper;
    }
}