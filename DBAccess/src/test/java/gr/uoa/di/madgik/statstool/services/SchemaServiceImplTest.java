package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SchemaServiceImpl#getFieldValues} — the filter-value lookup path.
 */
public class SchemaServiceImplTest {

    private static final int RESULT_LIMIT = 70;

    private StatsService statsService;
    private SchemaServiceImpl schemaService;

    @BeforeEach
    void setup() throws Exception {
        statsService = mock(StatsService.class);
        schemaService = new SchemaServiceImpl(statsService, mock(Mapper.class));

        Field f = SchemaServiceImpl.class.getDeclaredField("RESULT_LIMIT");
        f.setAccessible(true);
        f.set(schemaService, RESULT_LIMIT);
    }

    @SuppressWarnings("unchecked")
    private List<Query> captureQueries(int expectedCalls) throws Exception {
        ArgumentCaptor<List<Query>> captor = ArgumentCaptor.forClass(List.class);
        verify(statsService, times(expectedCalls)).query(captor.capture());
        return captor.getAllValues().stream()
                .map(batch -> {
                    assertEquals(1, batch.size());
                    return batch.get(0);
                })
                .toList();
    }

    private Query captureQuery() throws Exception {
        return captureQueries(1).get(0);
    }

    private static Result rowsOf(String... values) {
        Result r = new Result();
        for (String v : values) {
            r.addRow(List.of(v));
        }
        return r;
    }

    private static Result overLimitResult() {
        Result r = new Result();
        for (int i = 0; i < RESULT_LIMIT + 1; i++) {
            r.addRow(List.of("v" + i));
        }
        return r;
    }

    @Test
    void getFieldValues_boundsQueryToResultLimitPlusOne() throws Exception {
        when(statsService.query(anyList())).thenReturn(List.of(new Result()));

        schemaService.getFieldValues("openaire", "result.result.title", "");

        Query q = captureQuery();
        assertEquals(RESULT_LIMIT + 1, q.getLimit(), "query must be bounded, not unbounded (limit 0)");
        assertNull(q.getFilters(), "no like arg => no filter");
    }

    @Test
    void getFieldValues_pushesLikeDownAsContainsFilter() throws Exception {
        when(statsService.query(anyList())).thenReturn(List.of(new Result()));

        schemaService.getFieldValues("openaire", "result.result.title", "cancer");

        Query q = captureQuery();
        assertEquals(RESULT_LIMIT + 1, q.getLimit());

        assertNotNull(q.getFilters());
        assertEquals(1, q.getFilters().size());
        FilterGroup group = q.getFilters().get(0);
        assertEquals("AND", group.getOp());
        assertEquals(1, group.getGroupFilters().size());

        Filter filter = group.getGroupFilters().get(0);
        assertEquals("contains", filter.getType());
        assertEquals("result.title", filter.getField());
        assertEquals(List.of("cancer"), filter.getValues());
    }

    @Test
    void getFieldValues_returnsListWhenWithinLimit() throws Exception {
        when(statsService.query(anyList())).thenReturn(List.of(rowsOf("a", "b")));

        var fieldValues = schemaService.getFieldValues("openaire", "result.result.title", "");

        assertEquals(2, fieldValues.getCount());
        assertEquals(List.of("a", "b"), fieldValues.getValues());
    }

    @Test
    void getFieldValues_withinLimit_doesNotRunCountQuery() throws Exception {
        when(statsService.query(anyList())).thenReturn(List.of(rowsOf("a", "b")));

        schemaService.getFieldValues("openaire", "result.result.title", "");

        verify(statsService, times(1)).query(anyList());
    }

    @Test
    void getFieldValues_returnsTrueTotalWhenOverLimit() throws Exception {
        when(statsService.query(anyList()))
                .thenReturn(List.of(overLimitResult()))
                .thenReturn(List.of(rowsOf("1234")));

        var fieldValues = schemaService.getFieldValues("openaire", "result.result.title", "");

        assertEquals(1234, fieldValues.getCount());
        assertNull(fieldValues.getValues());
    }

    @Test
    void getFieldValues_countQueryUsesCountDistinctWithSameFilter() throws Exception {
        when(statsService.query(anyList()))
                .thenReturn(List.of(overLimitResult()))
                .thenReturn(List.of(rowsOf("1234")));

        schemaService.getFieldValues("openaire", "result.result.title", "cancer");

        List<Query> queries = captureQueries(2);
        Query listQuery = queries.get(0);
        Query countQuery = queries.get(1);

        assertNull(listQuery.getSelect().get(0).getAggregate());
        assertEquals("count", countQuery.getSelect().get(0).getAggregate());
        assertEquals("result.title", countQuery.getSelect().get(0).getField());
        assertEquals(listQuery.getFilters(), countQuery.getFilters());
    }

    @Test
    void getFieldValues_countUnparseable_fallsBackToSentinel() throws Exception {
        when(statsService.query(anyList()))
                .thenReturn(List.of(overLimitResult()))
                .thenReturn(List.of(rowsOf("not-a-number")));

        var fieldValues = schemaService.getFieldValues("openaire", "result.result.title", "");

        assertEquals(RESULT_LIMIT + 1, fieldValues.getCount());
        assertNull(fieldValues.getValues());
    }
}
