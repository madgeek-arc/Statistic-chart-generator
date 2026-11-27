package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    // Rule R2: merge chart series that differ only in the value of one equality filter on the
    // group-by field into one query + pivot. Off by default.
    @Value("${statstool.sql.merge-series:false}")
    private boolean mergeSeries;

    private final Logger log = LogManager.getLogger(this.getClass());

    void setMergeSeries(boolean mergeSeries) {
        this.mergeSeries = mergeSeries;
    }

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
                        NamedQueryResolution resolved = resolveNamedQuery(query);
                        querySql = resolved.sql();
                        parameters = resolved.parameters();
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

                boolean stackedOrder = "stacked".equals(orderBy) || "pinned".equals(orderBy) || "yaxis".equals(orderBy)
                        || "xaxis".equals(orderBy) || orderBy == null;
                int n = individualSqls.size();
                String effectiveOrderBy = effectiveOrderBy(orderBy, stackedOrder, n);
                String limitClause = limitClause(queryList);

                String finalSql;
                SeriesMerge seriesMerge = (mergeSeries && stackedOrder && xCount == 1)
                        ? tryMergeSeries(queryList, individualSqls, individualParams, orderBy) : null;
                if (seriesMerge != null) {
                    log.debug("R2: {} series merged into one query", n);
                    finalSql = seriesMerge.sql() + " ORDER BY " + effectiveOrderBy + limitClause;
                    mergedParameters = seriesMerge.parameters();
                } else {
                    // Build CTE column list: (y, x1) for single group-by, (y, x1, x2, ...) for multi.
                    // For "stacked" ordering all CTEs run unlimited (the outer query decides top-N by
                    // combined sum). Otherwise q1 keeps its ORDER BY + LIMIT so it defines the top-N
                    // and secondary CTEs are stripped so they return all rows for the LEFT JOIN.
                    // Null orderBy also uses the keys-CTE approach so that queries with disjoint
                    // x-axis values (e.g. stacked categorical charts) all appear on the x-axis.
                    // "pinned" also uses the keys-CTE so that q1's x-values are always present,
                    // then sorts q1-present rows first and the rest by combined sum DESC.
                    // "yaxis" also uses the keys-CTE so that disjoint-series queries (e.g. q1=Ireland,
                    // q2=all other countries) all appear on the x-axis rather than q1 silently
                    // defining the entire universe (which would suppress q2's exclusive rows).
                    // "xaxis" uses it too: categories present only in a later series must not be dropped.
                    // Joins on x are null-safe (IS NOT DISTINCT FROM) so a NULL category keeps its values.
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
                                         .append(" IS NOT DISTINCT FROM keys.x").append(xi);
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
                                         .append(" IS NOT DISTINCT FROM q1.x").append(xi);
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

                    finalSql = cteSql.toString() + selectT + finalSelect.toString()
                            + " ORDER BY " + effectiveOrderBy + limitClause;
                }

                Result mergedResult;
                if (allUseCache && statsCache.isEnabled()) {
                    String cacheKey = StatsCache.getCacheKey(finalSql, mergedParameters, baseProfile);
                    mergedResult = statsCache.get(cacheKey);
                    if (mergedResult != null) {
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

    // xaxis/null → ORDER BY x1 (alphabetical)
    // stacked    → ORDER BY COALESCE(y1,0)+COALESCE(y2,0)+...+COALESCE(yn,0) DESC
    // pinned     → ORDER BY CASE WHEN y1 IS NOT NULL THEN 0 ELSE 1 END, COALESCE sum DESC
    //              (q1's x-values appear first, remainder sorted by combined sum)
    // yaxis/else → ORDER BY 1 DESC  (first y column)
    private static String effectiveOrderBy(String orderBy, boolean stackedOrder, int n) {
        if (orderBy == null || orderBy.trim().isEmpty() || orderBy.equals("xaxis")) {
            return "x1";
        } else if ("pinned".equals(orderBy)) {
            StringBuilder pinnedOrder = new StringBuilder("CASE WHEN y1 IS NOT NULL THEN 0 ELSE 1 END, COALESCE(y1,0)");
            for (int i = 2; i <= n; i++) pinnedOrder.append("+COALESCE(y").append(i).append(",0)");
            pinnedOrder.append(" DESC");
            return pinnedOrder.toString();
        } else if (stackedOrder) {
            StringBuilder sum = new StringBuilder("COALESCE(y1,0)");
            for (int i = 2; i <= n; i++) sum.append("+COALESCE(y").append(i).append(",0)");
            sum.append(" DESC");
            return sum.toString();
        }
        return "1 DESC";
    }

    // LIMIT of the merged statement: min positive limit across queries
    private static String limitClause(List<Query> queryList) {
        int minLimit = Integer.MAX_VALUE;
        for (Query q : queryList) {
            int lim = q.getLimit();
            if (lim > 0 && lim < minLimit) minLimit = lim;
        }
        return minLimit != Integer.MAX_VALUE ? " LIMIT " + minLimit : "";
    }

    record SeriesMerge(String sql, List<Object> parameters) {}

    /**
     * Rule R2. When the series are description-based queries that are identical except the value of ONE
     * equality filter, and that filter is on the (single) group-by field (e.g. one series per SDG topic,
     * grouped by topic), each series returns only the row of its own value. One query with the values as an
     * IN list returns all those rows in one pass; a pivot puts row x = v_i into column y_i:
     * <pre>WITH m(y, x1) AS (&lt;merged query&gt;), t AS (SELECT MAX(CASE WHEN x1 = ? THEN y END) AS y1, ...,
     * x1 FROM m GROUP BY x1) SELECT y1, ..., yn, x1 FROM t</pre>
     * The caller appends the ORDER BY / LIMIT of the keys-CTE path, so it is only valid there (stacked order).
     * The series' generated SQL must be identical with parameters that differ in one position only, which
     * also gives the typed values for the pivot. Returns null when the rule does not apply.
     */
    SeriesMerge tryMergeSeries(List<Query> queryList, List<String> sqls, List<List<Object>> params, String orderBy) {
        int n = queryList.size();
        Query first = queryList.get(0);
        for (Query q : queryList) {
            if (q.getName() != null || q.getSelect() == null || q.getFilters() == null) return null;
        }

        // Generated SQL identical, parameters differ in exactly one position
        List<Object> p0 = params.get(0);
        if (p0 == null) return null;
        Set<Integer> diffPos = new LinkedHashSet<>();
        for (int i = 1; i < n; i++) {
            if (!sqls.get(0).equals(sqls.get(i))) return null;
            List<Object> pi = params.get(i);
            if (pi == null || pi.size() != p0.size()) return null;
            for (int j = 0; j < p0.size(); j++) {
                if (!Objects.equals(p0.get(j), pi.get(j))) diffPos.add(j);
            }
        }
        if (diffPos.size() != 1) return null;
        int pos = diffPos.iterator().next();
        List<Object> seriesValues = new ArrayList<>();
        for (List<Object> pi : params) {
            Object v = pi.get(pos);
            // plain "?" placeholder types only (double/number are written as CAST(? AS DOUBLE))
            if (!(v instanceof String || v instanceof Integer || v instanceof Long)) return null;
            seriesValues.add(v);
        }

        // Same entity, selects, limit and order; exactly one group-by select
        List<Select> sel0 = first.getSelect();
        String groupField = null;
        for (Select s : sel0) {
            if (s.getAggregate() == null || s.getAggregate().isEmpty()) {
                if (groupField != null) return null;
                groupField = s.getField();
            }
        }
        if (groupField == null) return null;
        for (Query q : queryList) {
            if (!Objects.equals(q.getEntity(), first.getEntity()) || q.getLimit() != first.getLimit()
                    || !Objects.equals(q.getOrderBy(), first.getOrderBy())
                    || !Objects.equals(q.getProfile(), first.getProfile())) return null;
            if (q.getSelect().size() != sel0.size()) return null;
            for (int j = 0; j < sel0.size(); j++) {
                Select a = sel0.get(j), b = q.getSelect().get(j);
                if (!Objects.equals(a.getField(), b.getField()) || !Objects.equals(a.getAggregate(), b.getAggregate())) return null;
            }
        }

        // Filters identical except one: field = group-by field, type "=", one value, in an AND group
        List<FilterGroup> fg0 = first.getFilters();
        int[] loc = null;
        for (Query q : queryList.subList(1, n)) {
            List<FilterGroup> fg = q.getFilters();
            if (fg.size() != fg0.size()) return null;
            for (int g = 0; g < fg0.size(); g++) {
                FilterGroup a = fg0.get(g), b = fg.get(g);
                if (!(a.getOp() == null ? b.getOp() == null : a.getOp().equalsIgnoreCase(b.getOp()))) return null;
                List<Filter> fa = a.getGroupFilters(), fb = b.getGroupFilters();
                if (fa == null || fb == null || fa.size() != fb.size()) return null;
                for (int f = 0; f < fa.size(); f++) {
                    if (fa.get(f).equals(fb.get(f))) continue;
                    if (loc != null && (loc[0] != g || loc[1] != f)) return null;
                    loc = new int[]{g, f};
                }
            }
        }
        if (loc == null) return null;
        FilterGroup group0 = fg0.get(loc[0]);
        if (group0.getGroupFilters().size() > 1 && !"AND".equalsIgnoreCase(group0.getOp())) return null;
        Set<String> values = new LinkedHashSet<>();
        for (Query q : queryList) {
            Filter f = q.getFilters().get(loc[0]).getGroupFilters().get(loc[1]);
            Filter f0 = group0.getGroupFilters().get(loc[1]);
            if (!groupField.equals(f.getField()) || !"=".equals(f.getType()) || f.getValues() == null
                    || f.getValues().size() != 1 || !Objects.equals(f.getDatatype(), f0.getDatatype())) return null;
            values.add(f.getValues().get(0));
        }
        if (values.size() < 2) return null;

        // The merged query: the common query with that filter's value list = all series' values
        List<FilterGroup> mergedFilters = new ArrayList<>();
        for (int g = 0; g < fg0.size(); g++) {
            List<Filter> fs = new ArrayList<>(fg0.get(g).getGroupFilters());
            if (g == loc[0]) {
                Filter f0 = fs.get(loc[1]);
                fs.set(loc[1], new Filter(f0.getField(), "=", new ArrayList<>(values), f0.getDatatype()));
            }
            mergedFilters.add(new FilterGroup(fs, fg0.get(g).getOp()));
        }
        Query merged = new Query(null, null, mergedFilters, sel0, first.getEntity(), first.getProfile(),
                first.getLimit(), first.getOrderBy(), first.isUseCache());
        List<Object> mergedParams = new ArrayList<>();
        String body = mapper.map(merged, mergedParams, orderBy);
        if (body == null) return null;
        // The value list replaces one parameter by |values|; anything else means the SQL shape changed
        if (mergedParams.size() != p0.size() - 1 + values.size()) return null;
        body = body.trim();
        if (body.endsWith(";")) body = body.substring(0, body.length() - 1);
        int orderByIdx = body.toUpperCase().lastIndexOf("ORDER BY");
        if (orderByIdx >= 0) body = body.substring(0, orderByIdx).trim();

        StringBuilder sql = new StringBuilder("WITH m(y, x1) AS (").append(body).append("), t AS (SELECT ");
        for (int i = 1; i <= n; i++) {
            sql.append("MAX(CASE WHEN x1 = ? THEN y END) AS y").append(i).append(", ");
        }
        sql.append("x1 FROM m GROUP BY x1) SELECT ");
        for (int i = 1; i <= n; i++) sql.append("y").append(i).append(", ");
        sql.append("x1 FROM t");
        List<Object> allParams = new ArrayList<>(mergedParams);
        allParams.addAll(seriesValues);
        return new SeriesMerge(sql.toString(), allParams);
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
                NamedQueryResolution resolved = resolveNamedQuery(query);
                querySql = resolved.sql();
                parameters = resolved.parameters();
            }

            // Log the generated SQL and parameters for inspection
            log.debug("Generated SQL: {}", querySql);
            log.debug("Bound parameters (in order): {}", parameters);
            log.debug("Target profile: {}", profile);

            if (query.isUseCache() && statsCache.isEnabled()) {
                cacheKey = StatsCache.getCacheKey(querySql, parameters, profile);
                result = statsCache.get(cacheKey);
                if (result != null) {
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

    @Override
    public Result queryRaw(QueryWithParameters queryWithParameters) throws StatsServiceException {
        try {
            boolean useCache = statsCache.isEnabled();
            String cacheKey = useCache ? StatsCache.getCacheKey(queryWithParameters) : null;

            if (useCache) {
                Result cached = statsCache.get(cacheKey);
                if (cached != null) {
                    log.debug("Raw query key {} in cache.", cacheKey);
                    return cached;
                }
            }
            log.info("Executing raw query {}", queryWithParameters.getQuery());
            TimedResult timedResult = statsRepository.executeQuery(
                    queryWithParameters.getQuery(),
                    queryWithParameters.getParameters(),
                    queryWithParameters.getDbId());
            if (useCache) {
                statsCache.save(queryWithParameters, timedResult.result, timedResult.execTimeMs, timedResult.queueTimeMs);
            }
            return timedResult.result;
        } catch (Exception e) {
            throw new StatsServiceException("Raw query execution failed: " + e.getMessage(), e);
        }
    }

    private String getNamedQuery(String queryName) throws IOException {
            return namedQueryRepository.getQuery(queryName);
    }

    private record NamedQueryResolution(String sql, List<Object> parameters) {}

    // Resolves a named query's SQL and bind parameters. When `namedParameters` is supplied, its
    // `:name` tokens are rewritten to positional `?` marks and the values flattened into a
    // matching ordered list - a List-valued parameter expands into one `?` per element, so
    // IN (:list) clauses are supported without array binding. Otherwise the query's positional
    // `parameters` list is used unchanged. Throws NamedParametersValidationException on invalid
    // `namedParameters` input.
    private NamedQueryResolution resolveNamedQuery(Query query) throws StatsServiceException, IOException {
        String querySql = getNamedQuery(query.getName());
        if (querySql == null) {
            throw new StatsServiceException("query " + query.getName() + " not found!");
        }

        Map<String, Object> namedParameters = query.getNamedParameters();
        List<Object> parameters = query.getParameters();

        if (namedParameters != null && !namedParameters.isEmpty()) {
            if (parameters != null && !parameters.isEmpty()) {
                throw new NamedParametersValidationException("Query '" + query.getName()
                        + "' specifies both 'parameters' and 'namedParameters'; use only one.");
            }
            for (Map.Entry<String, Object> e : namedParameters.entrySet()) {
                if (e.getValue() instanceof Collection<?> c && c.isEmpty()) {
                    throw new NamedParametersValidationException("Named parameter '" + e.getKey()
                            + "' is an empty list, which would produce an invalid IN () clause.");
                }
            }

            Set<String> tokens = extractNamedParameterTokens(querySql);

            List<String> missing = new ArrayList<>();
            for (String name : tokens) {
                if (!namedParameters.containsKey(name)) {
                    missing.add(name);
                }
            }
            if (!missing.isEmpty()) {
                throw new NamedParametersValidationException("Named query '" + query.getName()
                        + "' is missing required parameter(s): " + missing);
            }

            List<String> unreferenced = new ArrayList<>();
            for (String name : namedParameters.keySet()) {
                if (!tokens.contains(name)) {
                    unreferenced.add(name);
                }
            }
            if (!unreferenced.isEmpty()) {
                throw new NamedParametersValidationException("Query '" + query.getName()
                        + "' specifies namedParameters not referenced by the query: " + unreferenced);
            }

            ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(querySql);
            MapSqlParameterSource paramSource = new MapSqlParameterSource(namedParameters);
            querySql = NamedParameterUtils.substituteNamedParameters(parsedSql, paramSource);

            // One value array slot per named parameter, not per expanded `?` - a List-valued
            // parameter comes back as a single Iterable element, so flatten it (same order
            // used above to expand it in the SQL text) to line up with the `?` marks.
            Object[] rawValues = NamedParameterUtils.buildValueArray(parsedSql, paramSource, null);
            List<Object> flattened = new ArrayList<>();
            for (Object value : rawValues) {
                if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) {
                        flattened.add(item);
                    }
                } else {
                    flattened.add(value);
                }
            }
            parameters = flattened;
        }

        return new NamedQueryResolution(querySql, parameters);
    }

    // Collects every `:name` token referenced in the SQL, skipping string literals, "::" casts,
    // and "--"/"/* */" comments, so a missing-parameter error can list all missing names at once
    // rather than one.
    private static Set<String> extractNamedParameterTokens(String sql) {
        Set<String> names = new LinkedHashSet<>();
        boolean inSingleQuote = false;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (!inSingleQuote && c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int lineEnd = sql.indexOf('\n', i);
                i = (lineEnd < 0) ? sql.length() : lineEnd + 1;
                continue;
            }
            if (!inSingleQuote && c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int commentEnd = sql.indexOf("*/", i + 2);
                i = (commentEnd < 0) ? sql.length() : commentEnd + 2;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = !inSingleQuote;
                i++;
                continue;
            }
            if (!inSingleQuote && c == ':') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
                    i += 2; // Postgres "::" cast, not a named parameter
                    continue;
                }
                int j = i + 1;
                if (j < sql.length() && Character.isLetter(sql.charAt(j))) {
                    int start = j;
                    while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
                        j++;
                    }
                    names.add(sql.substring(start, j));
                    i = j;
                    continue;
                }
            }
            i++;
        }
        return names;
    }

}
