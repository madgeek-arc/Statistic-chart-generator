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
                        // Pass orderBy into each subquery so CTEs order consistently with the outer query
                        querySql = mapper.map(query, parameters, orderBy);
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

                // Build CTEs q1..qn with explicit (y, x) column naming to normalize outputs.
                // q1 is the primary series and keeps its ORDER BY + LIMIT (top-N by y).
                // Secondary CTEs (q2..qn) have ORDER BY and LIMIT stripped so they return
                // all rows; the LEFT JOIN below restricts them to x-values present in q1.
                cteSql.append("WITH ");
                for (int i = 0; i < individualSqls.size(); i++) {
                    if (i > 0) cteSql.append(", ");
                    String qi = "q" + (i + 1);
                    String subSql = individualSqls.get(i);
                    if (i > 0) {
                        // Strip trailing ORDER BY (and any LIMIT that follows it)
                        int orderByIdx = subSql.toUpperCase().lastIndexOf("ORDER BY");
                        if (orderByIdx >= 0) {
                            subSql = subSql.substring(0, orderByIdx).trim();
                        }
                    }
                    cteSql.append(qi).append("(y, x) AS (").append(subSql).append(")");
                    List<Object> params = individualParams.get(i);
                    if (params != null) mergedParameters.addAll(params);
                }

                // q1 drives the result set via LEFT JOIN; its x is always non-null so no COALESCE needed.
                int n = individualSqls.size();
                StringBuilder fromJoins = new StringBuilder("FROM q1");
                for (int i = 2; i <= n; i++) {
                    fromJoins.append(" LEFT JOIN q").append(i)
                            .append(" ON q").append(i).append(".x = q1.x");
                }

                // Build a temp CTE t that selects q1.x and all yi
                StringBuilder selectT = new StringBuilder();
                selectT.append(", t AS (SELECT q1.x AS x");
                for (int i = 1; i <= n; i++) {
                    selectT.append(", q").append(i).append(".y AS y").append(i);
                }
                selectT.append(" ").append(fromJoins).append(")");

                // Select all yi columns alongside x in a single row per x-value
                StringBuilder finalSelect = new StringBuilder(" SELECT ");
                for (int i = 1; i <= n; i++) {
                    if (i > 1) finalSelect.append(", ");
                    finalSelect.append("y").append(i);
                }
                finalSelect.append(", x FROM t");

                String finalSql = cteSql.toString() + selectT + finalSelect.toString();
                // Apply outer ORDER BY and LIMIT if provided/available.
                // Mirror SqlQueryTree's convention: xaxis/null → order by x (the join key);
                // anything else (e.g. "yaxis") → positional "1 DESC" (first y column).
                String effectiveOrderBy;
                if (orderBy == null || orderBy.trim().isEmpty() || orderBy.equals("xaxis")) {
                    effectiveOrderBy = "x";
                } else {
                    effectiveOrderBy = "1 DESC";
                }
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

                // Split the wide merged row [y1, y2, ..., yn, x] into N individual (yi, x) Results
                for (int i = 0; i < n; i++) {
                    Result r = new Result();
                    for (List<?> row : mergedResult.getRows()) {
                        List<Object> pair = new ArrayList<>();
                        pair.add(row.get(i)); // yi
                        pair.add(row.get(n)); // x
                        r.addRow(pair);
                    }
                    results.add(r);
                }
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
