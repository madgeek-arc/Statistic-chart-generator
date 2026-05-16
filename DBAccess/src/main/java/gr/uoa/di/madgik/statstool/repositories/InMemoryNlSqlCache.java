package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryNlSqlCache implements NlSqlCache {

    private final ConcurrentHashMap<String, QueryWithParameters> store = new ConcurrentHashMap<>();

    @Override
    public QueryWithParameters get(String profile, String canonicalNl) {
        return store.get(key(profile, canonicalNl));
    }

    @Override
    public void put(String profile, String canonicalNl, QueryWithParameters sqlResult) {
        store.put(key(profile, canonicalNl), sqlResult);
    }

    private String key(String profile, String canonicalNl) {
        return profile + "\0" + canonicalNl;
    }
}
