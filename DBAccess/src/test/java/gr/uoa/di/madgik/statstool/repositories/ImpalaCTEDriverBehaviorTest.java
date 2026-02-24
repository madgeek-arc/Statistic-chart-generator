package gr.uoa.di.madgik.statstool.repositories;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Characterization tests for the Simba JDBC driver (Impala) CTE behavior.
 *
 * <p>These tests document — and assert — the broken behavior of the Simba JDBC
 * driver when executing CTE (WITH) queries through standard JDBC APIs.  They
 * exist so that:
 * <ul>
 *   <li>The workarounds in {@link StatsRepository} and
 *       {@code StatsServiceImpl} are self-documenting.</li>
 *   <li>If the driver is ever upgraded and the bugs are fixed the tests will
 *       <em>fail</em>, signalling that the workarounds can be removed.</li>
 * </ul>
 *
 * <p><b>How to run:</b> supply the Impala JDBC URL (and optionally credentials)
 * as JVM system properties:
 * <pre>
 *   mvn test -pl DBAccess \
 *     -Dimpala.test.jdbc.url="jdbc:impala://host:21050/default;AuthMech=3" \
 *     -Dimpala.test.jdbc.user=myuser \
 *     -Dimpala.test.jdbc.password=secret
 * </pre>
 * All tests are <em>skipped</em> automatically when the property is absent or
 * when the connection cannot be established.
 */
public class ImpalaCTEDriverBehaviorTest {

    /**
     * Minimal CTE that mirrors the merged-query structure produced by
     * {@code StatsServiceImpl}: two CTEs, a FULL OUTER JOIN, and a UNION ALL.
     * Uses only literal values — no tables — so it works against any Impala
     * instance regardless of schema.
     */
    private static final String CTE_WITH_PARAMS =
            "WITH q1(y, x) AS (SELECT ?, ?)," +
            " q2(y, x) AS (SELECT ?, ?)," +
            " t AS (SELECT COALESCE(q1.x, q2.x) AS x, q1.y AS y1, q2.y AS y2" +
            "        FROM q1 FULL OUTER JOIN q2 ON q2.x = q1.x)" +
            " SELECT y1 AS y, x FROM t" +
            " UNION ALL SELECT y2 AS y, x FROM t" +
            " ORDER BY x";

    private static final String CTE_INLINED =
            "WITH q1(y, x) AS (SELECT 1, 'a')," +
            " q2(y, x) AS (SELECT 2, 'b')," +
            " t AS (SELECT COALESCE(q1.x, q2.x) AS x, q1.y AS y1, q2.y AS y2" +
            "        FROM q1 FULL OUTER JOIN q2 ON q2.x = q1.x)" +
            " SELECT y1 AS y, x FROM t" +
            " UNION ALL SELECT y2 AS y, x FROM t" +
            " ORDER BY x";

    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;

    @BeforeAll
    static void checkImpalaAvailable() {
        jdbcUrl = System.getProperty("impala.test.jdbc.url");
        assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
                "Skipping Impala CTE behavior tests: " +
                "set -Dimpala.test.jdbc.url=jdbc:impala://host:21050/default to enable");

        jdbcUser     = System.getProperty("impala.test.jdbc.user", "");
        jdbcPassword = System.getProperty("impala.test.jdbc.password", "");

        // Skip if the host is not reachable
        try (Connection c = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            assumeTrue(c != null, "Skipping: getConnection() returned null");
        } catch (SQLException e) {
            assumeTrue(false,
                    "Skipping: could not connect to Impala (" + e.getMessage() + ")");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    private static String toHiveJdbcUrl(String impalaUrl) {
        // Strip Simba-specific ;key=value parameters, swap scheme, disable SASL.
        // jdbc:impala://host:21050/db;UseNativeQuery=1 → jdbc:hive2://host:21050/db;auth=noSasl
        // Without auth=noSasl the Hive driver defaults to Kerberos and times out.
        String stripped = impalaUrl.contains(";")
                ? impalaUrl.substring(0, impalaUrl.indexOf(';'))
                : impalaUrl;
        return stripped.replace("jdbc:impala://", "jdbc:hive2://") + ";auth=noSasl";
    }

    // -------------------------------------------------------------------------
    // Expected failure: PreparedStatement with ? parameters
    // -------------------------------------------------------------------------

    /**
     * The Simba driver cannot populate parameter metadata for ? placeholders
     * that appear inside a CTE body, so PreparedStatement.executeQuery() throws
     * error 11420 before the query ever reaches Impala.
     *
     * <p>This is why {@link StatsRepository} catches 11420 and falls back to
     * re-preparing the statement with inlined SQL literals.
     */
    @Test
    void preparedStatement_withParams_throwsSimba11420() throws Exception {
        // NOTE: 11420 is thrown at prepareStatement() time, not at executeQuery().
        // The driver tries to resolve parameter metadata during statement preparation
        // and fails immediately for CTE queries that contain ? placeholders.
        try (Connection conn = connect()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> conn.prepareStatement(CTE_WITH_PARAMS),
                    "Simba should reject prepareStatement() with ? inside CTEs");
            assertTrue(ex.getMessage().contains("11420"),
                    "Expected Simba error 11420, got: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Expected failure: PreparedStatement with inlined SQL (no ?)
    // -------------------------------------------------------------------------

    /**
     * Even after inlining all parameters (removing every ?) the Simba driver
     * still rejects PreparedStatement.executeQuery() for CTE queries with
     * error 11300 "A ResultSet was expected but not generated".
     *
     * <p>This means re-preparing the inlined SQL via PreparedStatement does
     * <em>not</em> solve the problem, and the fallback in {@code StatsServiceImpl}
     * must run each subquery individually.
     */
    @Test
    void preparedStatement_inlinedSql_throwsSimba11300() throws Exception {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(CTE_INLINED)) {

            SQLException ex = assertThrows(SQLException.class, ps::executeQuery,
                    "Simba should reject PreparedStatement.executeQuery() for CTEs " +
                    "even when there are no ? parameters");
            assertTrue(ex.getMessage().contains("11300"),
                    "Expected Simba error 11300, got: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Expected failure: Statement.executeQuery
    // -------------------------------------------------------------------------

    /**
     * Plain {@code Statement.executeQuery()} is also rejected by the Simba
     * driver for CTE queries (error 11300).
     */
    @Test
    void statement_executeQuery_throwsSimba11300() throws Exception {
        try (Connection conn = connect();
             Statement st = conn.createStatement()) {

            SQLException ex = assertThrows(SQLException.class,
                    () -> st.executeQuery(CTE_INLINED),
                    "Simba should reject Statement.executeQuery() for CTEs");
            assertTrue(ex.getMessage().contains("11300"),
                    "Expected Simba error 11300, got: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Documented quirk: Statement.execute — runs but gives no ResultSet
    // -------------------------------------------------------------------------

    /**
     * {@code Statement.execute()} is the only JDBC call that actually sends the
     * CTE query to Impala (it does not throw), but the Simba driver incorrectly
     * returns {@code false} — signalling "no ResultSet" — even though the query
     * is a SELECT.  As a consequence {@link Statement#getResultSet()} returns
     * {@code null}, making the result inaccessible.
     *
     * <p>This is a driver bug: per the JDBC spec {@code execute()} must return
     * {@code true} when the first result is a ResultSet object.
     *
     * <p>If this test <em>fails</em> (i.e. {@code execute()} starts returning
     * {@code true} and {@code getResultSet()} is non-null) the driver has been
     * fixed and the workaround in {@code StatsServiceImpl} can be removed.
     */
    @Test
    void statement_execute_runsQueryButReturnsFalse_andNullResultSet() throws Exception {
        try (Connection conn = connect();
             Statement st = conn.createStatement()) {

            boolean hasResultSet = st.execute(CTE_INLINED);

            assertFalse(hasResultSet,
                    "Simba execute() returns false for CTE SELECT queries (driver bug). " +
                    "If this assertion fails the driver has been fixed — remove CTE workarounds.");
            assertNull(st.getResultSet(),
                    "getResultSet() must be null when execute() returned false (per JDBC spec). " +
                    "If this assertion fails the driver has been fixed — remove CTE workarounds.");
        }
    }

    // -------------------------------------------------------------------------
    // Probe: open-source Hive JDBC driver CTE compatibility
    // -------------------------------------------------------------------------

    /**
     * Probe: does the open-source Hive JDBC driver handle CTE queries with ?
     * parameters correctly when connected to Impala?
     *
     * <p>PASS → Hive driver is a viable replacement for Simba; workarounds in
     *           StatsRepository / StatsServiceImpl can be removed.
     * <p>FAIL → the error message documents how the Hive driver behaves.
     */
    @Test
    void hiveDriver_preparedStatement_withParams_canExecuteCTE() throws Exception {
        String hiveUrl = toHiveJdbcUrl(jdbcUrl);
        try (Connection conn = DriverManager.getConnection(hiveUrl, jdbcUser, jdbcPassword);
             PreparedStatement ps = conn.prepareStatement(CTE_WITH_PARAMS)) {
            ps.setObject(1, 1);
            ps.setObject(2, "a");
            ps.setObject(3, 2);
            ps.setObject(4, "b");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(),
                        "Hive driver should return at least one row from the CTE query");
            }
        }
    }
}
