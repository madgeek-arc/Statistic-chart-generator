package gr.uoa.di.madgik.statstool.repositories;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

@Repository
public class NamedQueryRepository {

    @Value("${named.queries.path}")
    private String namedQueriespath;

    @Value("${number.queries.path}")
    private String numbersPath;

    @Autowired
    ResourceLoader resourceLoader;

    public String getQuery(String name) throws IOException {
        Properties properties = new Properties();
        Resource resource = resourceLoader.getResource(namedQueriespath);

        properties.load(resource.getInputStream());

        return properties.getProperty(name);
    }

    public String getNumbersQuery(String name) throws IOException {
        Properties properties = new Properties();
        Resource resource = resourceLoader.getResource(numbersPath);

        properties.load(resource.getInputStream());

        return properties.getProperty(name);
    }

    public Properties getNumberQueries() throws IOException {
        Properties properties = new Properties();
        Resource resource = resourceLoader.getResource(numbersPath);

        properties.load(resource.getInputStream());

        return properties;
    }
}
