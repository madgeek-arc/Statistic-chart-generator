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
import gr.uoa.di.madgik.statstool.domain.TimedResult;
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

                // Pre-scan: determine xCount (number of grouping columns) from description-based
                // queries. All queries must agree; named queries are assumed to match.
                // Fall back if profiles differ or xCount is inconsistent across queries.
                int xCount = 1; // default for named-only query lists
                boolean xCountSet = false;
                for (Query query : queryList) {
                    String profile = query.getProfile() + ".public";
                    if (baseProfile == null) baseProfile = profile;
                    if (!baseProfile.equals(profile)) {
                        log.debug("Queries target different profiles ({} vs {}). Falling back to per-query execution.", baseProfile, profile);
                        return runIndividually(queryList, orderBy);
                    }
                    if (query.getName() == null && query.getSelect() != null) {
                        int qx = (int) query.getSelect().stream()
                                .filter(s -> s.getAggregate() == null || s.getAggregate().isEmpty())
                                .count();
                        if (!xCountSet) {
                            xCount = Math.max(qx, 1);
                            xCountSet = true;
                        } else if (qx != xCount) {
                            log.debug("Queries have inconsistent grouping column counts ({} vs {}). Falling back to per-query execution.", xCount, qx);
                            return runIndividually(queryList, orderBy);
                        }
                    }
                }

                for (Query query : queryList) {
                    List<Object> parameters = new ArrayList<>();
                    String queryName = query.getName();
                    String querySql;

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

                    // Strip trailing semicolon if exists
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

                // Build CTE column list: (y, x1) for single group-by, (y, x1, x2, ...) for multi.
                // For "stacked" ordering all CTEs run unlimited (the outer query decides top-N by
                // combined sum). Otherwise q1 keeps its ORDER BY + LIMIT so it defines the top-N
                // and secondary CTEs are stripped so they return all rows for the LEFT JOIN.
                // Null orderBy also uses the keys-CTE approach so that queries with disjoint
                // x-axis values (e.g. stacked categorical charts) all appear on the x-axis.
                boolean stackedOrder = "stacked".equals(orderBy) || orderBy == null;
                StringBuilder cteColumns = new StringBuilder("(y");
                for (int xi = 1; xi <= xCount; xi++) cteColumns.append(", x").append(xi);
                cteColumns.append(")");

                cteSql.append("WITH ");
                for (int i = 0; i < individualSqls.size(); i++) {
                    if (i > 0) cteSql.append(", ");
                    String qi = "q" + (i + 1);
                    String subSql = individualSqls.get(i);
                    if (i > 0 || stackedOrder) {
                        // Strip trailing ORDER BY (and any LIMIT that follows it)
                        int orderByIdx = subSql.toUpperCase().lastIndexOf("ORDER BY");
                        if (orderByIdx >= 0) subSql = subSql.substring(0, orderByIdx).trim();
                    }
                    cteSql.append(qi).append(cteColumns).append(" AS (").append(subSql).append(")");
                    List<Object> params = individualParams.get(i);
                    if (params != null) mergedParameters.addAll(params);
                }

                int n = individualSqls.size();
                StringBuilder selectT = new StringBuilder();

                if (stackedOrder) {
                    // Stacked mode: queries may return disjoint x-axis values (e.g. each series filters
                    // a different category). Build a "keys" CTE with the UNION of all x values so every
                    // category appears on the x-axis, then LEFT JOIN each query to it.
                    cteSql.append(", keys AS (SELECT ");
                    for (int xi = 1; xi <= xCount; xi++) {
                        if (xi > 1) cteSql.append(", ");
                        cteSql.append("x").append(xi);
                    }
                    cteSql.append(" FROM (");
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) cteSql.append(" UNION ALL ");
                        cteSql.append("SELECT ");
                        for (int xi = 1; xi <= xCount; xi++) {
                            if (xi > 1) cteSql.append(", ");
                            cteSql.append("x").append(xi);
                        }
                        cteSql.append(" FROM q").append(i);
                    }
                    cteSql.append(") all_keys GROUP BY ");
                    for (int xi = 1; xi <= xCount; xi++) {
                        if (xi > 1) cteSql.append(", ");
                        cteSql.append("x").append(xi);
                    }
                    cteSql.append(")");

                    StringBuilder fromJoins = new StringBuilder("FROM keys");
                    for (int i = 1; i <= n; i++) {
                        fromJoins.append(" LEFT JOIN q").append(i).append(" ON ");
                        for (int xi = 1; xi <= xCount; xi++) {
                            if (xi > 1) fromJoins.append(" AND ");
                            fromJoins.append("q").append(i).append(".x").append(xi)
                                     .append(" = keys.x").append(xi);
                        }
                    }

                    selectT.append(", t AS (SELECT ");
                    for (int xi = 1; xi <= xCount; xi++) {
                        if (xi > 1) selectT.append(", ");
                        selectT.append("keys.x").append(xi).append(" AS x").append(xi);
                    }
                    for (int i = 1; i <= n; i++) {
                        selectT.append(", q").append(i).append(".y AS y").append(i);
                    }
                    selectT.append(" ").append(fromJoins).append(")");
                } else {
                    // Non-stacked mode: q1 drives the x-axis (defines top-N via its ORDER BY + LIMIT).
                    StringBuilder fromJoins = new StringBuilder("FROM q1");
                    for (int i = 2; i <= n; i++) {
                        fromJoins.append(" LEFT JOIN q").append(i).append(" ON ");
                        for (int xi = 1; xi <= xCount; xi++) {
                            if (xi > 1) fromJoins.append(" AND ");
                            fromJoins.append("q").append(i).append(".x").append(xi)
                                     .append(" = q1.x").append(xi);
                        }
                    }

                    selectT.append(", t AS (SELECT ");
                    for (int xi = 1; xi <= xCount; xi++) {
                        if (xi > 1) selectT.append(", ");
                        selectT.append("q1.x").append(xi).append(" AS x").append(xi);
                    }
                    for (int i = 1; i <= n; i++) {
                        selectT.append(", q").append(i).append(".y AS y").append(i);
                    }
                    selectT.append(" ").append(fromJoins).append(")");
                }

                // Final SELECT: y1..yn, x1..xm FROM t
                StringBuilder finalSelect = new StringBuilder(" SELECT ");
                for (int i = 1; i <= n; i++) {
                    if (i > 1) finalSelect.append(", ");
                    finalSelect.append("y").append(i);
                }
                for (int xi = 1; xi <= xCount; xi++) finalSelect.append(", x").append(xi);
                finalSelect.append(" FROM t");

                String finalSql = cteSql.toString() + selectT + finalSelect.toString();
                // xaxis/null → ORDER BY x1
                // stacked    → ORDER BY COALESCE(y1,0)+COALESCE(y2,0)+...+COALESCE(yn,0) DESC
                // yaxis/else → ORDER BY 1 DESC  (first y column)
                String effectiveOrderBy;
                if (orderBy == null || orderBy.trim().isEmpty() || orderBy.equals("xaxis")) {
                    effectiveOrderBy = "x1";
                } else if (stackedOrder) {
                    StringBuilder sum = new StringBuilder("COALESCE(y1,0)");
                    for (int i = 2; i <= n; i++) sum.append("+COALESCE(y").append(i).append(",0)");
                    sum.append(" DESC");
                    effectiveOrderBy = sum.toString();
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
                        TimedResult timedResult = statsRepository.executeQuery(finalSql, mergedParameters, baseProfile);
                        mergedResult = timedResult.result;
                        statsCache.save(new QueryWithParameters(finalSql, mergedParameters, baseProfile), mergedResult, timedResult.execTimeMs, timedResult.queueTimeMs);
                    }
                } else {
                    log.debug("Cache disabled for at least one subquery. Executing merged SQL without cache.");
                    mergedResult = statsRepository.executeQuery(finalSql, mergedParameters, baseProfile).result;
                }

                // Split the wide merged row [y1, ..., yn, x1, ..., xm] into N individual Results.
                // Each Result row contains [yi, x1, ..., xm]: yi at index i, x columns at n..n+xCount-1.
                for (int i = 0; i < n; i++) {
                    Result r = new Result();
                    for (List<?> row : mergedResult.getRows()) {
                        List<Object> tuple = new ArrayList<>();
                        tuple.add(row.get(i)); // yi
                        for (int xi = 0; xi < xCount; xi++) tuple.add(row.get(n + xi)); // x1..xm
                        r.addRow(tuple);
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
                    TimedResult timedResult = statsRepository.executeQuery(querySql, parameters, profile);
                    result = timedResult.result;
                    log.debug("result: {}", result);

                    statsCache.save(new QueryWithParameters(querySql, parameters, profile), result, timedResult.execTimeMs, timedResult.queueTimeMs);
                }
            } else {
                log.debug("Cache disabled for query.");
                result = statsRepository.executeQuery(querySql, parameters, profile).result;
            }

            results.add(result);
        }
        return results;
    }

    private String getNamedQuery(String queryName) throws IOException {
            return namedQueryRepository.getQuery(queryName);
    }

}
