package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;

public interface NlSqlCache {

    QueryWithParameters get(String profile, String canonicalNl);

    void put(String profile, String canonicalNl, QueryWithParameters sqlResult);
}
