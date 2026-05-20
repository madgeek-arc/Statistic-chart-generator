package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;

import java.util.TreeMap;

public interface NlSqlCache {

    NlCachedEntry get(String profile, String canonicalNl, String schemaFingerprint);

    void put(String profile, String canonicalNl, NlCachedEntry entry, String schemaFingerprint);

    void evict(String profile, String canonicalNl);

    void drop(String profile);

    static String fingerprint(ProfileConfiguration config) {
        try {
            StringBuilder sb = new StringBuilder();
            new TreeMap<>(config.tables).forEach((entity, table) ->
                sb.append(entity).append("=").append(table.getTable()).append(";")
            );
            new TreeMap<>(config.fields).forEach((key, field) ->
                sb.append(key).append("=").append(field.getTable()).append(".").append(field.getColumn()).append(";")
            );
            return StatsCache.MD5(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }
}
