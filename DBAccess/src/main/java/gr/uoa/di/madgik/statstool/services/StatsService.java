package gr.uoa.di.madgik.statstool.services;

import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;

public interface StatsService {

    List<Result> query(List<Query> queryList) throws StatsServiceException;
}
