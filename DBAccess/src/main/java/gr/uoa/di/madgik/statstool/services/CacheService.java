package gr.uoa.di.madgik.statstool.services;

import java.util.Map;

public interface CacheService {

    public void updateCache();

    public void promoteCache();

    public void dropCache() throws Exception;

    public Map<String, Object> getStats() throws Exception;
}
