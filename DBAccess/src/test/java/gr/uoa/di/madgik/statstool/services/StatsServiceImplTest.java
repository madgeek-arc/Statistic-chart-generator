package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    private Query buildNamedQuery(String name, boolean useCache) {
        Query q = new Query();
        q.setName(name);
        q.setParameters(new ArrayList<>());
        q.setProfile("test");
        q.setUseCache(useCache);
        return q;
    }

    private Result resultWithRows(List<List<?>> rows) {
        Result r = new Result();
        for (List<?> row : rows) {
            r.addRow(row);
        }
        return r;
    }

    @BeforeEach
    void setup() {
        // no-op: using @InjectMocks
    }

    @Test
    void singleQuery_noMerge_returnsResultAsIs() throws Exception {
        Query q1 = buildNamedQuery("Q1", false);

        when(namedQueryRepository.getQuery("Q1")).thenReturn("SELECT ...");
        Result repoResult = resultWithRows(Arrays.asList(
                Arrays.asList("10", "A"),
                Arrays.asList("20", "B")
        ));
        when(statsRepository.executeQuery(anyString(), anyList(), eq("test.public"))).thenReturn(repoResult);

        List<Result> out = statsService.query(List.of(q1), null);

        assertNotNull(out);
        assertEquals(1, out.size());
        assertEquals(repoResult.getRows(), out.get(0).getRows());
    }

    @Test
    void multipleQueries_mergeTwoResults_intoSingleMergedResult() throws Exception {
        Query q1 = buildNamedQuery("S1", false);
        Query q2 = buildNamedQuery("S2", false);

        when(namedQueryRepository.getQuery(anyString())).thenReturn("SELECT ...");
        Result r1 = resultWithRows(Arrays.asList(
                Arrays.asList("1", "A"),
                Arrays.asList("2", "B")
        ));
        Result r2 = resultWithRows(Arrays.asList(
                Arrays.asList("3", "A"),
                Arrays.asList("4", "C")
        ));
        when(statsRepository.executeQuery(anyString(), anyList(), eq("test.public")))
                .thenReturn(r1)
                .thenReturn(r2);

        List<Result> out = statsService.query(List.of(q1, q2), null);

        assertNotNull(out);
        assertEquals(1, out.size());
        Result merged = out.get(0);
        // Expect 4 rows with third column being the series label
        assertEquals(4, merged.getRows().size());
        assertEquals(Arrays.asList("1", "A", "S1"), merged.getRows().get(0));
        assertEquals(Arrays.asList("2", "B", "S1"), merged.getRows().get(1));
        assertEquals(Arrays.asList("3", "A", "S2"), merged.getRows().get(2));
        assertEquals(Arrays.asList("4", "C", "S2"), merged.getRows().get(3));
    }

    @Test
    void multipleQueries_preserveThreeColumnRows_andAugmentTwoColumnRows() throws Exception {
        Query q1 = buildNamedQuery("S1", false);
        Query q2 = buildNamedQuery("S2", false);

        when(namedQueryRepository.getQuery(anyString())).thenReturn("SELECT ...");
        Result r1 = resultWithRows(Arrays.asList(
                Arrays.asList("5", "A", "Existing")
        ));
        Result r2 = resultWithRows(Arrays.asList(
                Arrays.asList("7", "B")
        ));
        when(statsRepository.executeQuery(anyString(), anyList(), eq("test.public")))
                .thenReturn(r1)
                .thenReturn(r2);

        List<Result> out = statsService.query(List.of(q1, q2), null);

        assertEquals(1, out.size());
        Result merged = out.get(0);
        assertEquals(2, merged.getRows().size());
        assertEquals(Arrays.asList("5", "A", "Existing"), merged.getRows().get(0));
        assertEquals(Arrays.asList("7", "B", "S2"), merged.getRows().get(1));
    }

    @Test
    void cacheHit_returnsCachedResult_withoutCallingRepository() throws Exception {
        Query q1 = buildNamedQuery("Qcache", true);

        when(namedQueryRepository.getQuery("Qcache")).thenReturn("SELECT ...");
        // Build cache key expectation loosely by simulating exists/get regardless of exact key
        when(statsCache.exists(anyString())).thenReturn(true);
        Result cached = resultWithRows(List.of(Arrays.asList("9", "Z")));
        when(statsCache.get(anyString())).thenReturn(cached);

        List<Result> out = statsService.query(List.of(q1), null);

        assertEquals(1, out.size());
        assertEquals(cached.getRows(), out.get(0).getRows());
        verify(statsRepository, never()).executeQuery(anyString(), anyList(), anyString());
        verify(statsCache, never()).save(any(), any(), anyInt());
    }

    @Test
    void namedQueryNotFound_throwsStatsServiceException() throws Exception {
        Query q1 = buildNamedQuery("Missing", false);
        when(namedQueryRepository.getQuery("Missing")).thenReturn(null);

        StatsServiceException ex = assertThrows(StatsServiceException.class, () ->
                statsService.query(List.of(q1), null)
        );
        assertTrue(ex.getMessage().contains("query Missing not found"));
    }
}
