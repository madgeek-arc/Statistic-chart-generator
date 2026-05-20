package gr.uoa.di.madgik.statstool.services;

import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;

public interface StatsService {

    List<Result> query(List<Query> queryList) throws StatsServiceException;

    List<Result> query(List<Query> chartQueries, String orderBy) throws StatsServiceException;

    /** Execute a pre-built SQL prepared statement, bypassing the query mapper. */
    Result queryRaw(QueryWithParameters queryWithParameters) throws StatsServiceException;
}
