package gr.uoa.di.madgik.statstool.repositories;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Properties;

@Repository
public class NamedQueryRepository {

    @Value("${statstool.namedqueries.path}")
    private String namedQueriesPath;

    @Autowired
    ResourceLoader resourceLoader;

    public String getQuery(String name) throws IOException {
        Properties properties = new Properties();
        Resource resource = resourceLoader.getResource(namedQueriesPath);

        properties.load(resource.getInputStream());

        return properties.getProperty(name);
    }
}
