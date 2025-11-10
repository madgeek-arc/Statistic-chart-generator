package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
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
            boolean mergeResults = queryList != null && queryList.size() > 1;
            Result merged = mergeResults ? new Result() : null;

            int idx = 0;
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

                // Log the generated SQL and parameters for inspection
                log.debug("Generated SQL: {}", querySql);
                log.debug("Bound parameters (in order): {}", parameters);
                log.debug("Target profile: {}", profile);

                if (query.isUseCache()) {
                    cacheKey = StatsCache.getCacheKey(querySql, parameters, profile);

                    if (statsCache.exists(cacheKey)) {
                        result = statsCache.get(cacheKey);

                        log.debug("Key " + cacheKey + " in cache! Returning: " + result);
                    } else {
                        log.info("Performing query " + querySql);
                        log.debug("result for key " + cacheKey + " not in cache. Querying db!");
                        long start = new Date().getTime();
                        result = statsRepository.executeQuery(querySql, parameters, profile);
                        log.debug("result: " + result);
                        long execTime = new Date().getTime() - start;

                        statsCache.save(new QueryWithParameters(querySql, parameters, profile), result, (int) execTime);
                    }
                } else {
                    log.debug("Cache disabled for query.");
                    result = statsRepository.executeQuery(querySql, parameters, profile);
                }

                if (mergeResults) {
                    // Build series label from query name or default
                    String seriesLabel = (queryName != null && !queryName.isEmpty()) ? queryName : ("Series " + (idx + 1));
                    for (List<?> row : result.getRows()) {
                        if (row == null || row.size() == 0) continue;
                        if (row.size() >= 3) {
                            // Already includes a second group by / series identifier, keep as-is
                            merged.addRow(row);
                        } else if (row.size() == 2) {
                            // Transform [y, x] -> [y, x, seriesLabel]
                            List<Object> newRow = new ArrayList<>(3);
                            newRow.add(row.get(0));
                            newRow.add(row.get(1));
                            newRow.add(seriesLabel);
                            merged.addRow(newRow);
                        } else {
                            // Unexpected shape; skip row but log
                            log.warn("Unexpected row size {} encountered while merging; skipping.", row.size());
                        }
                    }
                } else {
                    results.add(result);
                }
                idx++;
            }

            if (mergeResults) {
                log.debug("Merged {} queries into a single Result with {} rows.", queryList.size(), merged.getRows().size());
                results.clear();
                results.add(merged);
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
