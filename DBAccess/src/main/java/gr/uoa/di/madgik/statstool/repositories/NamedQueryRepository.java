package gr.uoa.di.madgik.statstool.repositories;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class NamedQueryRepository {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private final Logger log = LogManager.getLogger(this.getClass());

    @Value("${statstool.namedqueries.path}")
    private String namedQueriesPath;

    @Autowired
    ResourceLoader resourceLoader;

    public String getQuery(String name) throws IOException {
        Properties properties = new Properties();
        Resource resource = resourceLoader.getResource(namedQueriesPath);

        properties.load(resource.getInputStream());

        String query = properties.getProperty(name);
        if (query == null) {
            return null;
        }
        return resolvePlaceholders(name, query, properties);
    }

    // Resolves ${otherKey} references against properties from the same file, so a value
    // shared by many named queries (e.g. a country list) can be defined once and reused.
    // A reference with no matching property is left untouched (just warned about) rather
    // than failing the query, since ${...} is also reserved syntax for Hive/Impala's own
    // server-side variable substitution - an unresolved token may be meant for the DB engine.
    private String resolvePlaceholders(String name, String query, Properties properties) {
        Matcher m = PLACEHOLDER.matcher(query);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = properties.getProperty(key);
            if (value == null) {
                log.warn("Named query '{}' references undefined property '{}' - forwarding placeholder as-is", name, key);
                value = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
