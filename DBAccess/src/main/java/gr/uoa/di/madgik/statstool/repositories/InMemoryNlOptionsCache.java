package gr.uoa.di.madgik.statstool.repositories;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryNlOptionsCache implements NlOptionsCache {

    private record Entry(String promptVersion, String optionsJson) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String get(String library, String canonicalDescription, String promptVersion) {
        Entry entry = store.get(key(library, canonicalDescription));
        if (entry == null || !entry.promptVersion().equals(promptVersion)) return null;
        return entry.optionsJson();
    }

    @Override
    public void put(String library, String canonicalDescription, String optionsJson, String promptVersion) {
        store.put(key(library, canonicalDescription), new Entry(promptVersion, optionsJson));
    }

    @Override
    public void evict(String library, String canonicalDescription) {
        store.remove(key(library, canonicalDescription));
    }

    @Override
    public void drop(String library) {
        if (library == null) {
            store.clear();
        } else {
            String prefix = library + "\0";
            store.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    private String key(String library, String desc) {
        return library + "\0" + desc;
    }
}
