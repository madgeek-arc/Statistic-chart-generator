package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;

import java.util.List;

public interface StatsService {

    List<Result> query(List<Query> queryList) throws StatsServiceException;

    List<Result> query(List<Query> chartQueries, String orderBy) throws StatsServiceException;
}
