package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;

import java.security.MessageDigest;
import java.util.List;

public interface StatsCache {
    static String getCacheKey(String query, List<Object> parameters, String dbId) throws Exception {
        return getCacheKey(new QueryWithParameters(query, parameters, dbId));
    }

    static String getCacheKey(QueryWithParameters query) throws Exception {
        return MD5(query.toString());
    }

    void dropCache() throws Exception;

    boolean exists(String key) throws Exception;

    Result get(String key) throws Exception;

    String save(QueryWithParameters fullSqlQuery, Result result) throws Exception;

    void storeEntry(CacheEntry entry) throws Exception;

    List<CacheEntry> getEntries();

    void deleteEntry(String key);

    static String MD5(String string) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");

        md.update(string.getBytes());

        byte[] byteData = md.digest();
        StringBuilder sb = new StringBuilder();

        for (byte aByteData : byteData) {
            sb.append(Integer.toString((aByteData & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
    }
}
