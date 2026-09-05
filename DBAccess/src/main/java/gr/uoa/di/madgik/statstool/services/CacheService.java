package gr.uoa.di.madgik.statstool.services;

import java.util.Map;

public interface CacheService {

    default void updateCache(String profile) {
        updateCache(profile, null, null);
    }

    void updateCache(String profile, Integer limit, Integer maxSeconds);

    void stopUpdate();

    void trickleUpdate(String profile);

    void promoteCache(String profile);

    void dropCache(String profile) throws Exception;

    Map<String, Object> getStats() throws Exception;

    void dropNlCache(String profile);

    void evictNlCache(String profile, String canonicalNl);

    void dropNlOptionsCache(String library);

    void evictNlOptionsCache(String library, String canonicalDescription);
}
