package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.TimedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        when(rs.getMetaData()).thenReturn(rsmd);
        when(rsmd.getColumnCount()).thenReturn(1);
        when(rs.next()).thenReturn(false); // no rows

        // Run tasks synchronously: when execute() is called with a PrioritizedFutureTask,
        // run it immediately on the calling thread so get() returns the result.
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
    }

    private StatsRepository newRepo() {
        return new StatsRepository(dataSource, executorService);
    }

    /**
     * Helper that unwraps ExecutionException to expose the underlying cause.
     * When the callable throws an exception inside a FutureTask, future.get()
     * wraps it in ExecutionException; we unwrap to get the root cause.
     */
    private static <T extends Throwable> T assertThrowsWrapped(Class<T> type,
            org.junit.jupiter.api.function.Executable executable) {
        ExecutionException ee = assertThrows(ExecutionException.class, executable);
        Throwable cause = ee.getCause();
        assertTrue(type.isInstance(cause),
                "Expected cause " + type.getName() + " but got " + cause);
        return type.cast(cause);
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
        // Validation occurs inside the FutureTask callable; the exception is wrapped
        // in ExecutionException by future.get().
        IllegalArgumentException ex = assertThrowsWrapped(IllegalArgumentException.class,
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
        // Validation occurs inside the FutureTask callable; the exception is wrapped
        // in ExecutionException by future.get().
        IllegalArgumentException ex = assertThrowsWrapped(IllegalArgumentException.class,
                () -> repo.executeQuery(sql, params, "p.public"));
        assertTrue(ex.getMessage().toLowerCase().contains("placeholder count"));
        verify(dataSource, never()).getConnection();
    }
}
