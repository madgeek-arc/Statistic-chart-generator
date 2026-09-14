package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.TimedResult;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StatsServiceImpl merge/refactor behavior.
 */
public class StatsServiceImplTest {

    @Mock
    private StatsRepository statsRepository;
    @Mock
    private StatsCache statsCache;
    @Mock
    private NamedQueryRepository namedQueryRepository;
    @Mock
    private Mapper mapper;

    @InjectMocks
    private StatsServiceImpl statsService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private Query newQuery(String profile, boolean useCache) {
        Query q = new Query();
        q.setProfile(profile);
        q.setUseCache(useCache);
        return q;
    }

    @Test
    void multipleQueriesSameProfile_areMergedWithUnionAll_andOrderByApplied() throws Exception {
        // Arrange two description-based queries with same profile
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        // mapper.map is called with the same orderBy passed to the merged query
        doAnswer(invocation -> {
            List<Object> params = invocation.getArgument(1);
            params.addAll(Arrays.asList(1, "A"));
            return "SELECT ? AS y, ? AS x"; // [y, x]
        }).when(mapper).map(eq(q1), anyList(), eq("xaxis"));

        doAnswer(invocation -> {
            List<Object> params = invocation.getArgument(1);
            params.addAll(Arrays.asList(2, "B"));
            return "SELECT ? AS y, ? AS x";
        }).when(mapper).map(eq(q2), anyList(), eq("xaxis"));

        // Repository returns a simple merged result
        Result merged = new Result();
        merged.setRows(new ArrayList<>());
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(new TimedResult(merged, 10, 5));

        // Act — pass "xaxis" so the merged ORDER BY resolves to the x column
        List<Result> results = statsService.query(Arrays.asList(q1, q2), "xaxis");

        // Assert - two individual results, one per query
        assertEquals(2, results.size());

        // Capture SQL and parameters passed to repository
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Object>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> profileCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository, times(1)).executeQuery(sqlCaptor.capture(), paramsCaptor.capture(), profileCaptor.capture());

        String sql = sqlCaptor.getValue();
        List<Object> params = paramsCaptor.getValue();
        String profile = profileCaptor.getValue();

        // SQL should contain WITH CTEs, LEFT JOIN (q1 drives), q1.x as the join key, and outer ORDER BY x
        assertTrue(sql.toUpperCase().contains("WITH"), "Expected WITH CTE in merged SQL, got: " + sql);
        assertTrue(sql.toUpperCase().contains("LEFT JOIN"), "Expected LEFT JOIN in merged SQL, got: " + sql);
        assertFalse(sql.toUpperCase().contains("FULL OUTER JOIN"), "Expected no FULL OUTER JOIN in merged SQL, got: " + sql);
        assertTrue(sql.contains("ORDER BY x1"), "Expected ORDER BY x1 in merged SQL, got: " + sql);

        // Multi-column SELECT: y1 and y2 selected together, no UNION ALL
        assertTrue(sql.contains("y1"), "Expected y1 column in merged SQL, got: " + sql);
        assertTrue(sql.contains("y2"), "Expected y2 column in merged SQL, got: " + sql);
        assertFalse(sql.toUpperCase().contains("UNION ALL"), "Expected no UNION ALL in merged SQL, got: " + sql);

        // Parameters must be concatenated in the same order as subqueries
        assertEquals(Arrays.asList(1, "A", 2, "B"), params);

        // Profile should be the profile + ".public"
        assertEquals("p.public", profile);

        // Cache save should be invoked once (since exists=false)
        verify(statsCache, times(1)).save(any(), eq(merged), anyInt(), anyInt());
    }

    @Test
    void multipleQueriesDifferentProfiles_fallbacksToIndividualExecution() throws Exception {
        Query q1 = newQuery("p1", true);
        Query q2 = newQuery("p2", true);

        // Profile mismatch is detected in the pre-scan before any mapper call is made.
        // runIndividually then calls mapper.map with the provided orderBy for each query.
        when(mapper.map(eq(q1), anyList(), eq("x DESC"))).thenReturn("SELECT 1, 'A'");
        when(mapper.map(eq(q2), anyList(), eq("x DESC"))).thenReturn("SELECT 2, 'B'");

        Result r1 = new Result();
        Result r2 = new Result();
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString()))
                .thenReturn(new TimedResult(r1, 10, 5))
                .thenReturn(new TimedResult(r2, 10, 5));

        List<Result> list = statsService.query(Arrays.asList(q1, q2), "x DESC");

        // Two separate results expected
        assertEquals(2, list.size());
        // Repository should have been called twice (no merging)
        verify(statsRepository, times(2)).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void mergedPath_usesCacheWhenAvailable_andSkipsDbCall() throws Exception {
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        // simple subqueries — orderBy=null flows through to mapper
        when(mapper.map(eq(q1), anyList(), isNull())).thenReturn("SELECT 1, 'A'");
        when(mapper.map(eq(q2), anyList(), isNull())).thenReturn("SELECT 2, 'B'");

        // Cache hit for merged key
        Result cached = new Result();
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(cached);

        List<Result> list = statsService.query(Arrays.asList(q1, q2), null);

        // Merged result is split into one Result per query
        assertEquals(2, list.size());

        // No repository call on cache hit
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void mergedPath_stackedOrderBy_generatesCombinedSumAndStripsAllLimits() throws Exception {
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        // For stacked, all CTEs are stripped — mapper is called with "stacked" but the
        // ORDER BY/LIMIT it generates inside each CTE will be removed before execution.
        when(mapper.map(eq(q1), anyList(), eq("stacked"))).thenReturn("SELECT 1, 'A' ORDER BY 1 DESC");
        when(mapper.map(eq(q2), anyList(), eq("stacked"))).thenReturn("SELECT 2, 'B' ORDER BY 1 DESC");

        Result merged = new Result();
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(new TimedResult(merged, 10, 5));

        statsService.query(Arrays.asList(q1, q2), "stacked");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), anyList(), anyString());

        String sql = sqlCaptor.getValue();
        // All CTEs must have no ORDER BY (including q1)
        // Count occurrences: only the outer ORDER BY should remain
        int orderByCount = (sql.toUpperCase().split("ORDER BY", -1).length - 1);
        assertEquals(1, orderByCount, "Expected exactly one ORDER BY (outer), got: " + sql);

        // Outer ORDER BY must be the COALESCE sum expression
        assertTrue(sql.contains("COALESCE(y1,0)+COALESCE(y2,0)"), "Expected stacked sum in ORDER BY, got: " + sql);
        assertTrue(sql.toUpperCase().contains("ORDER BY COALESCE(Y1,0)+COALESCE(Y2,0)"), "Expected stacked ORDER BY, got: " + sql);
    }

    @Test
    void stackedMode_usesKeysCte_toAlignDisjointXaxisValues() throws Exception {
        // Regression: stacked charts whose queries return disjoint x-axis values (e.g. each
        // series filters a different category) previously produced only the categories present in
        // q1. The fix adds a "keys" CTE from UNION ALL of all x columns, and joins every query to
        // it, so all categories appear even when individual queries return non-overlapping x values.
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);
        Query q3 = newQuery("p", true);

        when(mapper.map(eq(q1), anyList(), eq("stacked"))).thenReturn("SELECT 10, 'Open Access'");
        when(mapper.map(eq(q2), anyList(), eq("stacked"))).thenReturn("SELECT 5,  'Embargo'");
        when(mapper.map(eq(q3), anyList(), eq("stacked"))).thenReturn("SELECT 3,  'Restricted'");

        Result merged = new Result();
        merged.setRows(new ArrayList<>());
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(new TimedResult(merged, 10, 5));

        statsService.query(Arrays.asList(q1, q2, q3), "stacked");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), anyList(), anyString());
        String sql = sqlCaptor.getValue();

        // Must contain the keys CTE
        assertTrue(sql.contains("keys AS ("), "Stacked mode must include a 'keys' CTE to collect all x values");
        // keys CTE must union all individual CTEs
        assertTrue(sql.contains("FROM q1") && sql.contains("FROM q2") && sql.contains("FROM q3"),
                "keys CTE must include x values from all CTEs");
        assertTrue(sql.toUpperCase().contains("UNION ALL"), "keys CTE must use UNION ALL to merge x values");
        // Final FROM must drive from keys, not q1
        assertTrue(sql.contains("FROM keys LEFT JOIN q1"), "Final join must drive from keys, not q1");
        assertTrue(sql.contains("LEFT JOIN q2") && sql.contains("LEFT JOIN q3"),
                "All series must be LEFT JOINed to keys");
    }

    @Test
    void pinnedMode_usesKeysCte_andOrdersByQ1PresenceFirst() throws Exception {
        // "pinned" mode: q1 contains a fixed reference (e.g. Ireland); q2 contains the rest.
        // The keys CTE guarantees q1's x-values always appear, and the ORDER BY puts rows
        // where y1 IS NOT NULL (q1's entries) before the rest, which are sorted by combined sum DESC.
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        when(mapper.map(eq(q1), anyList(), eq("pinned"))).thenReturn("SELECT 10, 'Ireland'");
        when(mapper.map(eq(q2), anyList(), eq("pinned"))).thenReturn("SELECT 20, 'Germany'");

        Result merged = new Result();
        merged.setRows(new ArrayList<>());
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(new TimedResult(merged, 10, 5));

        statsService.query(Arrays.asList(q1, q2), "pinned");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), anyList(), anyString());
        String sql = sqlCaptor.getValue();

        // Must use keys CTE (stackedOrder=true)
        assertTrue(sql.contains("keys AS ("), "Pinned mode must use keys CTE, got: " + sql);
        assertTrue(sql.toUpperCase().contains("UNION ALL"), "keys CTE must UNION ALL queries, got: " + sql);
        assertTrue(sql.contains("FROM keys LEFT JOIN q1"), "Final join must drive from keys, got: " + sql);

        // ORDER BY must pin y1-present rows first, then sort by sum
        assertTrue(sql.contains("CASE WHEN y1 IS NOT NULL THEN 0 ELSE 1 END"),
                "Pinned mode must pin y1-present rows first, got: " + sql);
        assertTrue(sql.contains("COALESCE(y1,0)+COALESCE(y2,0)"),
                "Pinned mode must sort remainder by combined sum, got: " + sql);
    }

    @Test
    void mergedPath_yaxisOrderBy_usesKeysCteAndCoaclesceSum() throws Exception {
        // yaxis must use the UNION keys CTE so disjoint-series queries all appear on the
        // x-axis. Ordering is by COALESCE sum so each country ranks by its actual value
        // regardless of which series it belongs to (ORDER BY y1 alone would bury countries
        // with NULL y1 at an undefined position).
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        when(mapper.map(eq(q1), anyList(), eq("yaxis"))).thenReturn("SELECT 1, 'A'");
        when(mapper.map(eq(q2), anyList(), eq("yaxis"))).thenReturn("SELECT 2, 'B'");

        Result merged = new Result();
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(new TimedResult(merged, 10, 5));

        statsService.query(Arrays.asList(q1, q2), "yaxis");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), anyList(), anyString());

        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains("ORDER BY yaxis"), "yaxis must not appear literally in merged SQL, got: " + sql);
        // Keys CTE must be present so all x-values from all queries appear
        assertTrue(sql.contains("keys AS ("), "yaxis mode must include a keys CTE, got: " + sql);
        assertTrue(sql.toUpperCase().contains("UNION ALL"), "keys CTE must use UNION ALL, got: " + sql);
        assertTrue(sql.contains("FROM keys LEFT JOIN q1"), "Final join must drive from keys, got: " + sql);
        // Ordering must be COALESCE sum so countries with NULL in one series still rank correctly
        assertTrue(sql.contains("COALESCE(y1,0)+COALESCE(y2,0)"), "yaxis must use COALESCE sum ordering, got: " + sql);
    }

    @Test
    void yaxisMode_usesKeysCte_toAlignDisjointXaxisValues() throws Exception {
        // Regression: a chart with q1=Ireland-only and q2=all-other-countries previously
        // returned only Ireland because q1 drove the x-axis (FROM q1 LEFT JOIN q2). With the
        // fix, yaxis uses the UNION keys CTE so all countries from both queries appear.
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        when(mapper.map(eq(q1), anyList(), eq("yaxis"))).thenReturn("SELECT 100, 'Ireland'");
        when(mapper.map(eq(q2), anyList(), eq("yaxis"))).thenReturn("SELECT 200, 'Germany'");

        Result merged = new Result();
        merged.setRows(new ArrayList<>());
        when(statsCache.isEnabled()).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(null);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(new TimedResult(merged, 10, 5));

        statsService.query(Arrays.asList(q1, q2), "yaxis");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), anyList(), anyString());
        String sql = sqlCaptor.getValue();

        assertTrue(sql.contains("keys AS ("), "keys CTE must be present, got: " + sql);
        assertTrue(sql.contains("FROM q1") && sql.contains("FROM q2"), "keys CTE must reference both CTEs, got: " + sql);
        assertTrue(sql.toUpperCase().contains("UNION ALL"), "keys CTE must UNION ALL queries, got: " + sql);
        assertTrue(sql.contains("FROM keys LEFT JOIN q1"), "Final join must drive from keys, got: " + sql);
        assertTrue(sql.contains("LEFT JOIN q2"), "q2 must be LEFT JOINed, got: " + sql);
        // q1 must not drive — the old broken pattern
        assertFalse(sql.matches("(?s).*FROM q1\\s+LEFT JOIN q2.*"), "q1 must not drive x-axis unilaterally, got: " + sql);
    }

    @Test
    void namedQuery_withoutNamedParameters_usesPositionalParametersUnchanged() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setParameters(Arrays.asList("value1"));

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t WHERE col1 = ?");

        Result result = new Result();
        when(statsRepository.executeQuery(anyString(), anyList(), anyString()))
                .thenReturn(new TimedResult(result, 1, 1));

        List<Result> results = statsService.query(Arrays.asList(q));
        assertEquals(1, results.size());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Object>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), paramsCaptor.capture(), anyString());

        assertEquals("SELECT * FROM t WHERE col1 = ?", sqlCaptor.getValue());
        assertEquals(Arrays.asList("value1"), paramsCaptor.getValue());
    }

    @Test
    void namedQuery_withNamedParameters_resolvesToPositionalSqlAndExpandsListParams() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setNamedParameters(Map.of(
                "param1", "value1",
                "param2", Arrays.asList("a", "b", "c")
        ));

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t WHERE col1 = :param1 AND col2 IN (:param2)");

        Result result = new Result();
        when(statsRepository.executeQuery(anyString(), anyList(), anyString()))
                .thenReturn(new TimedResult(result, 1, 1));

        List<Result> results = statsService.query(Arrays.asList(q));
        assertEquals(1, results.size());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Object>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), paramsCaptor.capture(), anyString());

        String sql = sqlCaptor.getValue();
        List<Object> params = paramsCaptor.getValue();

        // No named tokens should remain, only positional `?` marks: one for param1, three for the list
        assertFalse(sql.contains(":param1"), "Expected :param1 to be resolved, got: " + sql);
        assertFalse(sql.contains(":param2"), "Expected :param2 to be resolved, got: " + sql);
        assertEquals(4, sql.chars().filter(c -> c == '?').count(), "Expected 4 total placeholders, got: " + sql);

        assertEquals(4, params.size());
        assertEquals("value1", params.get(0));
        assertEquals(Arrays.asList("a", "b", "c"), params.subList(1, 4));
    }

    @Test
    void namedQuery_withBothParametersAndNamedParameters_throws() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setParameters(Arrays.asList("value1"));
        q.setNamedParameters(Map.of("param1", "value1"));

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t WHERE col1 = :param1");

        StatsServiceException ex = assertThrows(StatsServiceException.class,
                () -> statsService.query(Arrays.asList(q)));
        assertInstanceOf(NamedParametersValidationException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("both 'parameters' and 'namedParameters'"),
                "Expected conflict message, got: " + ex.getCause().getMessage());
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void namedQuery_withEmptyListNamedParameter_throws() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setNamedParameters(Map.of("param2", new ArrayList<>()));

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t WHERE col2 IN (:param2)");

        StatsServiceException ex = assertThrows(StatsServiceException.class,
                () -> statsService.query(Arrays.asList(q)));
        assertInstanceOf(NamedParametersValidationException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("param2"),
                "Expected message to name the empty parameter, got: " + ex.getCause().getMessage());
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void namedQuery_withMissingNamedParameterValue_reportsAllMissingNames() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setNamedParameters(Map.of("param1", "value1")); // SQL also references :param2 and :param3

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t WHERE col1 = :param1 AND col2 = :param2 AND col3 = :param3");

        StatsServiceException ex = assertThrows(StatsServiceException.class,
                () -> statsService.query(Arrays.asList(q)));
        assertInstanceOf(NamedParametersValidationException.class, ex.getCause());
        String message = ex.getCause().getMessage();
        assertTrue(message.contains("param2"), "Expected missing param2 to be named, got: " + message);
        assertTrue(message.contains("param3"), "Expected missing param3 to be named, got: " + message);
        assertFalse(message.contains("param1"), "param1 was supplied and should not be reported missing, got: " + message);
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void namedQuery_withUnreferencedNamedParameter_throws() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setNamedParameters(Map.of("param1", "value1", "param2", "value2")); // SQL only references :param1

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t WHERE col1 = :param1");

        StatsServiceException ex = assertThrows(StatsServiceException.class,
                () -> statsService.query(Arrays.asList(q)));
        assertInstanceOf(NamedParametersValidationException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("param2"),
                "Expected unreferenced param2 to be named, got: " + ex.getCause().getMessage());
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void namedQuery_withCommentContainingColonToken_isNotTreatedAsRequiredParameter() throws Exception {
        Query q = newQuery("p", false);
        q.setName("some.named.query");
        q.setNamedParameters(Map.of("param1", "value1"));

        when(namedQueryRepository.getQuery("some.named.query"))
                .thenReturn("SELECT * FROM t -- filter by :notAParam later\n" +
                        "WHERE col1 = :param1 /* also mentions :stillNotAParam here */");

        Result result = new Result();
        when(statsRepository.executeQuery(anyString(), anyList(), anyString()))
                .thenReturn(new TimedResult(result, 1, 1));

        List<Result> results = statsService.query(Arrays.asList(q));
        assertEquals(1, results.size());

        ArgumentCaptor<List<Object>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statsRepository).executeQuery(anyString(), paramsCaptor.capture(), anyString());
        assertEquals(Arrays.asList("value1"), paramsCaptor.getValue());
    }
}
