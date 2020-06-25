package gr.uoa.di.madgik.statstool.services;

public interface CacheService {

    void promoteCache();

    public void calculateNumbers() throws StatsServiceException;

    public void promoteNumbers();

    public void updateCache();
}
