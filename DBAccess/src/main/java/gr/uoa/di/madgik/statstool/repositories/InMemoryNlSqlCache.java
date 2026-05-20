package gr.uoa.di.madgik.statstool.repositories;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryNlSqlCache implements NlSqlCache {

    private record Entry(String fingerprint, NlCachedEntry cached) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public NlCachedEntry get(String profile, String canonicalNl, String schemaFingerprint) {
        Entry entry = store.get(key(profile, canonicalNl));
        if (entry == null || !entry.fingerprint().equals(schemaFingerprint)) return null;
        return entry.cached();
    }

    @Override
    public void put(String profile, String canonicalNl, NlCachedEntry cached, String schemaFingerprint) {
        store.put(key(profile, canonicalNl), new Entry(schemaFingerprint, cached));
    }

    @Override
    public void evict(String profile, String canonicalNl) {
        store.remove(key(profile, canonicalNl));
    }

    @Override
    public void drop(String profile) {
        if (profile == null) {
            store.clear();
        } else {
            String prefix = profile + "\0";
            store.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    private String key(String profile, String canonicalNl) {
        return profile + "\0" + canonicalNl;
    }
}
