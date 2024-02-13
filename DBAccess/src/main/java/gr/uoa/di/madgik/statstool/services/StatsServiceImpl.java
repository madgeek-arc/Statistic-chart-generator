package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;

@Service
public class StatsServiceImpl implements StatsService {

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private StatsCache statsCache;

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
                String profile = query.getProfile() + ".public";

                log.debug("query: " + query);

                if (queryName == null) {
                    log.debug("Building query from description");
                    querySql = mapper.map(query, parameters, orderBy);
                } else {
                    log.debug("Retrieving named sql query from repository");
                    querySql = getNamedQuery(queryName);
                    parameters = query.getParameters();

                    if (querySql == null)
                        throw new StatsServiceException("query " + queryName + " not found!");
                }

                if (query.isUseCache()) {
                    cacheKey = StatsCache.getCacheKey(querySql, parameters, profile);

                    if (statsCache.exists(cacheKey)) {
                        result = statsCache.get(cacheKey);

                        log.debug("Key " + cacheKey + " in cache! Returning: " + result);
                    } else {
                        log.debug("result for key " + cacheKey + " not in cache. Querying db!");
                        result = statsRepository.executeQuery(querySql, parameters, profile);
                        log.debug("result: " + result);
                        statsCache.save(new QueryWithParameters(querySql, parameters, profile), result);
                    }
                } else {
                    log.debug("Cache disabled for query.");
                    result = statsRepository.executeQuery(querySql, parameters, profile);
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
