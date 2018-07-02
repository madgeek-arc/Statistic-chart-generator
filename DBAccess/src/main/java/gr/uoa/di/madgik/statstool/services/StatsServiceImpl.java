package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;

@Service
public class StatsServiceImpl implements StatsService{

    private StatsRepository statsRepository;

    private StatsRedisRepository statsRedisRepository;

    private Mapper mapper;

    public StatsServiceImpl(StatsRepository statsRepository, StatsRedisRepository statsRedisRepository, Mapper mapper) {
        this.statsRepository = statsRepository;
        this.statsRedisRepository = statsRedisRepository;
        this.mapper = mapper;
    }

    @Override
    public List<Result> query(List<Query> queryList) {
        List<Result> results = new ArrayList<>();
        for(Query query : queryList) {
            List<Object> parameters = new ArrayList<>();
            String querySql = mapper.map(query, parameters);
            String fullSqlQuery = statsRepository.getFullQuery(querySql, parameters);
            System.out.println("SQL: " + fullSqlQuery);
            Result result = statsRedisRepository.get(fullSqlQuery);
            if(result != null) {
                results.add(result);
                continue;
            }
            result = statsRepository.executeQuery(querySql, parameters);
            statsRedisRepository.save(fullSqlQuery, result);
            results.add(result);
        }
        return results;
    }
}
