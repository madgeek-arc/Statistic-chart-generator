package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;

@Service
public class StatsServiceImpl implements StatsService {

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private StatsRedisRepository statsRedisRepository;

    @Autowired
    private NamedQueryRepository namedQueryRepository;

    @Autowired
    private Mapper mapper;

    private final Logger log = LogManager.getLogger(this.getClass());

    @Override
    public List<Result> query(List<Query> queryList) throws StatsServiceException {
        return this.query(queryList, null);
    }

    @Override
    public List<Result> query(List<Query> queryList, String orderBy) throws StatsServiceException {
        List<Result> results = new ArrayList<>();

        try {
            for (Query query : queryList) {
                List<Object> parameters = new ArrayList<>();
                String queryName = query.getName();
                Result result;
                String querySql;
                String cacheKey;

                log.info("query: " + query);

                if (queryName == null) {
                    log.info("Building query from description");
                    querySql = mapper.map(query, parameters, orderBy);
                } else {
                    log.info("Retrieving named sql query from repository");
                    querySql = getNamedQuery(queryName);
                    parameters = query.getParameters();

                    if (querySql == null)
                        throw new StatsServiceException("query " + queryName + " not found!");
                }

                cacheKey = StatsRedisRepository.getCacheKey(querySql, parameters);

                if (statsRedisRepository.exists(cacheKey)) {
                    result = statsRedisRepository.get(cacheKey);

                    log.info("Key " + cacheKey + " in cache! Returning: " + result);
                } else {
                    log.info("result for key " + cacheKey + " not in cache. Querying db!");
                    result = statsRepository.executeQuery(querySql, parameters);
                    log.info("result: " + result);
                    statsRedisRepository.save(new QueryWithParameters(querySql, parameters), result);
                }

                results.add(result);
            }
        } catch (Exception e) {
            throw new StatsServiceException(e);
        }

        return results;
    }

    private String getNamedQuery(String queryName) throws IOException {
            return namedQueryRepository.getQuery(queryName);
    }
}
