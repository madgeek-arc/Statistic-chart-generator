package gr.uoa.di.madgik.statstool.services;

public interface CacheService {

    public void calculateNumbers() throws StatsServiceException;

    public void promoteNumbers();

    public void updateCache();
}
