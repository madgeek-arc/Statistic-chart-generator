package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static gr.uoa.di.madgik.statstool.repositories.StatsRepository.inlineParameters;
import static gr.uoa.di.madgik.statstool.repositories.StatsRepository.toSqlLiteral;

public class StatsRepositoryBindingTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement ps;
    private Statement stmt;
    private ResultSet rs;
    private ResultSetMetaData rsmd;
    private ExecutorService executorService;

    @BeforeEach
    void setup() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        ps = mock(PreparedStatement.class);
        stmt = mock(Statement.class);
        rs = mock(ResultSet.class);
        rsmd = mock(ResultSetMetaData.class);
        executorService = mock(ExecutorService.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(connection.createStatement()).thenReturn(stmt);
        when(ps.executeQuery()).thenReturn(rs);
        when(stmt.execute(anyString())).thenReturn(true);
        when(stmt.getResultSet()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(rsmd);
        when(rsmd.getColumnCount()).thenReturn(1);
        when(rs.next()).thenReturn(false); // no rows

        // run tasks synchronously by executing callable immediately and returning an already-completed Future
        when(executorService.submit(any(StatsRepository.ResultCallable.class))).thenAnswer(invocation -> {
            CallableWrapper callable = new CallableWrapper((StatsRepository.ResultCallable) invocation.getArgument(0));
            try {
                // execute immediately; let exceptions bubble as-is
                Result res = callable.call();
                return new ImmediateFuture(res);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                // unwrap IllegalArgumentException if it's the cause
                if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
                throw new RuntimeException(e);
            }
        });
    }
    
    private static class ImmediateFuture implements Future<Result> {
        private final Result result;
        ImmediateFuture(Result result) { this.result = result; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public Result get() { return result; }
        @Override public Result get(long timeout, java.util.concurrent.TimeUnit unit) { return result; }
    }

    private static class CallableWrapper {
        private final StatsRepository.ResultCallable callable;
        CallableWrapper(StatsRepository.ResultCallable callable) { this.callable = callable; }
        Result call() throws Exception { return callable.call(); }
    }

    private StatsRepository newRepo() {
        return new StatsRepository(dataSource, executorService);
    }

    @Test
    void bindsMixedTypes_withSetObject_inOrder() throws Exception {
        StatsRepository repo = newRepo();
        String sql = "SELECT ?, ?, ?, ?, ?, ?";
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        List<Object> params = Arrays.asList(
                "s", 42, 42L, 3.14, true, ts
        );

        repo.executeQuery(sql, params, "p.public");

        verify(ps).setObject(1, "s");
        verify(ps).setObject(2, 42);
        verify(ps).setObject(3, 42L);
        verify(ps).setObject(4, 3.14);
        verify(ps).setObject(5, true);
        verify(ps).setObject(6, ts);

        // And do not rely on parameter metadata
        verify(ps, never()).getParameterMetaData();
    }

    @Test
    void bindsZeroParams_executesWithoutSetters() throws Exception {
        StatsRepository repo = newRepo();
        String sql = "SELECT 1"; // no placeholders
        repo.executeQuery(sql, Collections.emptyList(), "p.public");

        verify(ps, never()).setObject(anyInt(), any());
        verify(ps, never()).setString(anyInt(), anyString());
        verify(ps, times(1)).executeQuery();
    }

    @Test
    void throwsOnNullParameter_beforeExecution() throws Exception {
        StatsRepository repo = newRepo();
        String sql = "SELECT ?";
        List<Object> params = Collections.singletonList(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> repo.executeQuery(sql, params, "p.public"));
        assertTrue(ex.getMessage().toLowerCase().contains("null parameter"));
        // Should fail before opening connection
        verify(dataSource, never()).getConnection();
    }

    @Test
    void throwsOnPlaceholderParameterCountMismatch() throws Exception {
        StatsRepository repo = newRepo();
        String sql = "SELECT ?, ?";
        List<Object> params = Collections.singletonList("only-one");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> repo.executeQuery(sql, params, "p.public"));
        assertTrue(ex.getMessage().toLowerCase().contains("placeholder count"));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void fallsBackToStatement_onSimba11420Error() throws Exception {
        // Simulate Simba JDBC error 11420 on PreparedStatement execution
        when(ps.executeQuery()).thenThrow(new SQLException("[Simba][JDBC](11420) Error, parameter metadata not populated."));

        StatsRepository repo = newRepo();
        String sql = "WITH q1(y,x) AS (SELECT COUNT(*) FROM t WHERE type=?) SELECT y,x FROM q1";
        List<Object> params = Collections.singletonList("publication");

        repo.executeQuery(sql, params, "p.public");

        // Should have fallen back to plain Statement with inlined SQL
        String expectedSql = "WITH q1(y,x) AS (SELECT COUNT(*) FROM t WHERE type='publication') SELECT y,x FROM q1";
        verify(stmt).execute(expectedSql);
        verify(stmt).getResultSet();
    }

    // --- inlineParameters unit tests ---

    @Test
    void inlineParameters_replacesStringParam() {
        String sql = "SELECT * FROM t WHERE type=?";
        String result = inlineParameters(sql, Collections.singletonList("publication"));
        assertEquals("SELECT * FROM t WHERE type='publication'", result);
    }

    @Test
    void inlineParameters_escapesInternalSingleQuotes() {
        String result = inlineParameters("SELECT ?", Collections.singletonList("O'Brien"));
        assertEquals("SELECT 'O''Brien'", result);
    }

    @Test
    void inlineParameters_handlesNumericParam() {
        String result = inlineParameters("SELECT ?", Collections.singletonList(42));
        assertEquals("SELECT 42", result);
    }

    @Test
    void inlineParameters_skipsPlaceholdersInsideStringLiterals() {
        // The ? inside a quoted literal must not be replaced
        String sql = "SELECT '?' AS q, ?";
        String result = inlineParameters(sql, Collections.singletonList("val"));
        assertEquals("SELECT '?' AS q, 'val'", result);
    }

    @Test
    void inlineParameters_multipleParams_inOrder() {
        String sql = "WHERE a=? AND b=?";
        String result = inlineParameters(sql, Arrays.asList("x", 99));
        assertEquals("WHERE a='x' AND b=99", result);
    }

    @Test
    void toSqlLiteral_string() {
        assertEquals("'hello'", toSqlLiteral("hello"));
    }

    @Test
    void toSqlLiteral_integer() {
        assertEquals("42", toSqlLiteral(42));
    }

    @Test
    void toSqlLiteral_double() {
        assertEquals("3.14", toSqlLiteral(3.14));
    }

    @Test
    void toSqlLiteral_boolean() {
        assertEquals("true", toSqlLiteral(true));
    }
}
