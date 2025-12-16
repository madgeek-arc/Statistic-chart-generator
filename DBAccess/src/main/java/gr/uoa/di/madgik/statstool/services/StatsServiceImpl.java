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
            // If there are multiple queries, try to merge them into a single SQL using CTEs and FULL OUTER JOINs to align on x, then unpivot y's
            if (queryList != null && queryList.size() > 1) {
                String baseProfile = null;
                boolean allUseCache = true;
                StringBuilder cteSql = new StringBuilder();
                List<Object> mergedParameters = new ArrayList<>();

                List<String> individualSqls = new ArrayList<>();
                List<List<Object>> individualParams = new ArrayList<>();

                for (Query query : queryList) {
                    List<Object> parameters = new ArrayList<>();
                    String queryName = query.getName();
                    String querySql;

                    String profile = query.getProfile() + ".public";
                    if (baseProfile == null) {
                        baseProfile = profile;
                    }

                    // If profiles differ, fall back to default behavior (cannot merge across DBs)
                    if (!baseProfile.equals(profile)) {
                        log.debug("Queries target different profiles ({} vs {}). Falling back to per-query execution.", baseProfile, profile);
                        return runIndividually(queryList, orderBy);
                    }

                    log.debug("query: {}", query);

                    if (queryName == null) {
                        log.debug("Building query from description");
                        // When merging, avoid applying orderBy inside each subquery to keep SQL valid
                        querySql = mapper.map(query, parameters, null);
                    } else {
                        log.debug("Retrieving named sql query from repository");
                        querySql = getNamedQuery(queryName);
                        parameters = query.getParameters();
                        if (querySql == null)
                            throw new StatsServiceException("query " + queryName + " not found!");
                    }

                    // Strip trailing semicolon if exists to safely UNION
                    querySql = querySql.trim();
                    if (querySql.endsWith(";")) {
                        querySql = querySql.substring(0, querySql.length() - 1);
                    }

                    individualSqls.add(querySql);
                    individualParams.add(parameters);
                    if (!query.isUseCache()) {
                        allUseCache = false;
                    }
                }

                // Build CTEs q1..qn with explicit (y, x) column naming to normalize outputs
                cteSql.append("WITH ");
                for (int i = 0; i < individualSqls.size(); i++) {
                    if (i > 0) cteSql.append(", ");
                    String qi = "q" + (i + 1);
                    cteSql.append(qi).append("(y, x) AS (").append(individualSqls.get(i)).append(")");
                    List<Object> params = individualParams.get(i);
                    if (params != null) mergedParameters.addAll(params);
                }

                // Build FULL OUTER JOIN chain and COALESCE expression for x
                int n = individualSqls.size();
                StringBuilder coalesceAll = new StringBuilder("COALESCE(");
                for (int i = 1; i <= n; i++) {
                    if (i > 1) coalesceAll.append(", ");
                    coalesceAll.append("q").append(i).append(".x");
                }
                coalesceAll.append(")");

                StringBuilder fromJoins = new StringBuilder("FROM q1");
                for (int i = 2; i <= n; i++) {
                    // coalesce previous xs for ON clause
                    StringBuilder prevCoalesce = new StringBuilder("COALESCE(");
                    for (int j = 1; j < i; j++) {
                        if (j > 1) prevCoalesce.append(", ");
                        prevCoalesce.append("q").append(j).append(".x");
                    }
                    prevCoalesce.append(")");
                    fromJoins.append(" FULL OUTER JOIN q").append(i)
                            .append(" ON q").append(i).append(".x = ")
                            .append(prevCoalesce);
                }

                // Build a temp CTE t that selects coalesced x and all yi
                StringBuilder selectT = new StringBuilder();
                selectT.append(", t AS (SELECT ")
                        .append(coalesceAll).append(" AS x");
                for (int i = 1; i <= n; i++) {
                    selectT.append(", q").append(i).append(".y AS y").append(i);
                }
                selectT.append(" ").append(fromJoins).append(")");

                // Unpivot y1..yn into two-column (y, x) result with UNION ALL to maintain formatter expectations
                StringBuilder finalSelect = new StringBuilder();
                finalSelect.append(" SELECT y1 AS y, x FROM t");
                for (int i = 2; i <= n; i++) {
                    finalSelect.append(" UNION ALL SELECT y").append(i).append(" AS y, x FROM t");
                }

                String finalSql = cteSql.toString() + selectT + finalSelect.toString();
                // Apply outer ORDER BY and LIMIT if provided/available
                String effectiveOrderBy = (orderBy != null && !orderBy.trim().isEmpty()) ? orderBy : "x";
                finalSql += " ORDER BY " + effectiveOrderBy;

                // Derive LIMIT as min positive limit across queries
                int minLimit = Integer.MAX_VALUE;
                for (Query q : queryList) {
                    int lim = q.getLimit();
                    if (lim > 0 && lim < minLimit) minLimit = lim;
                }
                if (minLimit != Integer.MAX_VALUE) {
                    finalSql += " LIMIT " + minLimit;
                }

                Result mergedResult;
                if (allUseCache) {
                    String cacheKey = StatsCache.getCacheKey(finalSql, mergedParameters, baseProfile);
                    if (statsCache.exists(cacheKey)) {
                        mergedResult = statsCache.get(cacheKey);
                        log.debug("Merged key {} in cache! Returning cached result.", cacheKey);
                    } else {
                        log.info("Performing merged query {}", finalSql);
                        long start = new Date().getTime();
                        mergedResult = statsRepository.executeQuery(finalSql, mergedParameters, baseProfile);
                        long execTime = new Date().getTime() - start;
                        statsCache.save(new QueryWithParameters(finalSql, mergedParameters, baseProfile), mergedResult, (int) execTime);
                    }
                } else {
                    log.debug("Cache disabled for at least one subquery. Executing merged SQL without cache.");
                    mergedResult = statsRepository.executeQuery(finalSql, mergedParameters, baseProfile);
                }

                results.add(mergedResult);
                return results;
            }

            // Fallback: original behavior for zero or single query
            return runIndividually(queryList, orderBy);
        } catch (Exception e) {
            throw new StatsServiceException(e);
        }
    }

    private List<Result> runIndividually(List<Query> queryList, String orderBy) throws Exception {
        List<Result> results = new ArrayList<>();
        for (Query query : queryList) {
            List<Object> parameters = new ArrayList<>();
            String queryName = query.getName();
            Result result;
            String querySql;
            String cacheKey;
            String profile = query.getProfile() + ".public";

            log.debug("query: {}", query);

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

                    log.debug("Key {} in cache! Returning: {}", cacheKey, result);
                } else {
                    log.info("Performing query {}", querySql);
                    log.debug("result for key {} not in cache. Querying db!", cacheKey);
                    long start = new Date().getTime();
                    result = statsRepository.executeQuery(querySql, parameters, profile);
                    log.debug("result: {}", result);
                    long execTime = new Date().getTime() - start;

                    statsCache.save(new QueryWithParameters(querySql, parameters, profile), result, (int) execTime);
                }
            } else {
                log.debug("Cache disabled for query.");
                result = statsRepository.executeQuery(querySql, parameters, profile);
            }

            results.add(result);
        }
        return results;
    }

    private String getNamedQuery(String queryName) throws IOException {
            return namedQueryRepository.getQuery(queryName);
    }
}
