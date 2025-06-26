package gr.uoa.di.madgik.statstool.services;

import java.util.Map;

public interface CacheService {

    void updateCache(String profile);

    void promoteCache(String profile);

    void dropCache(String profile) throws Exception;

    Map<String, Object> getStats() throws Exception;
}
