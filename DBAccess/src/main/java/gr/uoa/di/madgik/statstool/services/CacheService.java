package gr.uoa.di.madgik.statstool.services;

public interface CacheService {

    public void updateCache();

    public void promoteCache();

    public void dropCache() throws Exception;
}
