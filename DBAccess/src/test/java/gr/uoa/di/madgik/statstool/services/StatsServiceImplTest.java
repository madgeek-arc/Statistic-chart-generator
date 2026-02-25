package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
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
        when(statsCache.exists(anyString())).thenReturn(false);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(merged);

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

        // SQL should contain WITH CTEs, FULL OUTER JOIN alignment, COALESCE on x, and outer ORDER BY x
        assertTrue(sql.toUpperCase().contains("WITH"), "Expected WITH CTE in merged SQL, got: " + sql);
        assertTrue(sql.toUpperCase().contains("FULL OUTER JOIN"), "Expected FULL OUTER JOIN in merged SQL, got: " + sql);
        assertTrue(sql.toUpperCase().contains("COALESCE"), "Expected COALESCE in merged SQL, got: " + sql);
        assertTrue(sql.contains("ORDER BY x"), "Expected ORDER BY x in merged SQL, got: " + sql);

        // Multi-column SELECT: y1 and y2 selected together, no UNION ALL
        assertTrue(sql.contains("y1"), "Expected y1 column in merged SQL, got: " + sql);
        assertTrue(sql.contains("y2"), "Expected y2 column in merged SQL, got: " + sql);
        assertFalse(sql.toUpperCase().contains("UNION ALL"), "Expected no UNION ALL in merged SQL, got: " + sql);

        // Parameters must be concatenated in the same order as subqueries
        assertEquals(Arrays.asList(1, "A", 2, "B"), params);

        // Profile should be the profile + ".public"
        assertEquals("p.public", profile);

        // Cache save should be invoked once (since exists=false)
        verify(statsCache, times(1)).save(any(), eq(merged), anyInt());
    }

    @Test
    void multipleQueriesDifferentProfiles_fallbacksToIndividualExecution() throws Exception {
        Query q1 = newQuery("p1", true);
        Query q2 = newQuery("p2", true);

        // For fallback path, mapper.map will be called with provided orderBy, but
        // the implementation will also call mapper.map for the first query with orderBy=null
        // before detecting the profile mismatch on the second query.
        when(mapper.map(eq(q1), anyList(), eq("x DESC"))).thenReturn("SELECT 1, 'A'");
        when(mapper.map(eq(q2), anyList(), eq("x DESC"))).thenReturn("SELECT 2, 'B'");
        // Allow initial merged-attempt call with null orderBy for q1
        when(mapper.map(eq(q1), anyList(), isNull())).thenReturn("SELECT 1, 'A'");

        Result r1 = new Result();
        Result r2 = new Result();
        when(statsCache.exists(anyString())).thenReturn(false);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString()))
                .thenReturn(r1)
                .thenReturn(r2);

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
        when(statsCache.exists(anyString())).thenReturn(true);
        when(statsCache.get(anyString())).thenReturn(cached);

        List<Result> list = statsService.query(Arrays.asList(q1, q2), null);

        // Merged result is split into one Result per query
        assertEquals(2, list.size());

        // No repository call on cache hit
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
    }

    @Test
    void mergedPath_yaxisOrderBy_translatesTo1Desc() throws Exception {
        Query q1 = newQuery("p", true);
        Query q2 = newQuery("p", true);

        when(mapper.map(eq(q1), anyList(), eq("yaxis"))).thenReturn("SELECT 1, 'A'");
        when(mapper.map(eq(q2), anyList(), eq("yaxis"))).thenReturn("SELECT 2, 'B'");

        Result merged = new Result();
        when(statsCache.exists(anyString())).thenReturn(false);
        when(statsRepository.executeQuery(anyString(), anyList(), anyString())).thenReturn(merged);

        statsService.query(Arrays.asList(q1, q2), "yaxis");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statsRepository).executeQuery(sqlCaptor.capture(), anyList(), anyString());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("ORDER BY 1 DESC"), "Expected ORDER BY 1 DESC for yaxis, got: " + sql);
        assertFalse(sql.contains("ORDER BY yaxis"), "yaxis must not appear literally in merged SQL, got: " + sql);
    }
}
