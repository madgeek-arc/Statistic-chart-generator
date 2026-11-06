package gr.uoa.di.madgik.statstool.repositories;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class NamedQueryRepository {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private final Logger log = LogManager.getLogger(this.getClass());

    @Autowired
    ConfigRepository configRepository;

    private volatile ConcurrentHashMap<String, String> queryCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    /** Hot-reload all named queries from the DB. */
    public synchronized void reload() {
        Map<String, String> loaded = configRepository.loadNamedQueries();
        ConcurrentHashMap<String, String> fresh = new ConcurrentHashMap<>(loaded);
        this.queryCache = fresh;
        log.info("NamedQueryRepository reloaded: {} queries", fresh.size());
    }

    public String getQuery(String name) {
        String query = queryCache.get(name);
        if (query == null) return null;
        return resolvePlaceholders(name, query);
    }

    // Resolves ${otherKey} references against other entries in the same cache, so a value
    // shared by many named queries (e.g. a country list) can be defined once and reused.
    // A reference with no matching entry is left untouched (just warned about) rather than
    // failing the query, since ${...} is also reserved syntax for Hive/Impala's own
    // server-side variable substitution — an unresolved token may be meant for the DB engine.
    private String resolvePlaceholders(String name, String query) {
        Matcher m = PLACEHOLDER.matcher(query);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = queryCache.get(key);
            if (value == null) {
                log.warn("Named query '{}' references undefined key '{}' — forwarding as-is",
                    name, key);
                value = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
