package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;

@Service
public class StatsServiceImpl implements StatsService{

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private StatsRedisRepository statsRedisRepository;

    @Autowired
    private NamedQueryRepository namedQueryRepository;

    @Autowired
    private Mapper mapper;

    private final Logger log = Logger.getLogger(this.getClass());

    @Override
    public List<Result> query(List<Query> queryList) {
        List<Result> results = new ArrayList<>();

        for (Query query : queryList) {
            List<Object> parameters = new ArrayList<>();

            log.info("query: " + query);

            String queryName = query.getName();
            Result result;

            if (queryName == null) {
                String querySql = mapper.map(query, parameters);
                String fullSqlQuery = statsRepository.getFullQuery(querySql, parameters);

                result = statsRedisRepository.get(fullSqlQuery);

                if (result == null) {
                    result = statsRepository.executeQuery(querySql, parameters);
                    statsRedisRepository.save(fullSqlQuery, result);
                }
            } else {
                String querySql = getNamedQuery(queryName);

                log.debug("Query name: " + queryName);
                log.debug("Query: " + querySql);

                if (querySql == null) {
                    log.error("query " + queryName + " not found!");
                    continue;
                }

                if (query.getParameters() != null) {
                    for (int i = 1; i <= query.getParameters().size(); i++)
                        querySql = querySql.replaceAll("\\$"+i, "'" + query.getParameters().get(i-1) + "'");
                }

                log.debug("Query after replace:" + querySql);

                result = statsRedisRepository.get(querySql);

                if (result == null) {
                    result = statsRepository.executeQuery(querySql, new ArrayList<>());
                    statsRedisRepository.save(querySql, result);
                }
            }

            results.add(result);
        }
        return results;
    }

    private String getNamedQuery(String queryName) {
        try {
            return namedQueryRepository.getQuery(queryName);
        } catch (IOException e) {
            log.error("Error getting named query", e);

            return null;
        }
    }
}
