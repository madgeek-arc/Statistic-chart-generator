package gr.uoa.di.madgik.statstool.repositories;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test that verifies the Apache Hive JDBC driver can execute CTE
 * queries with {@code ?} parameters against Impala.
 *
 * <p><b>How to run:</b> supply the Impala JDBC URL (and optionally credentials)
 * as JVM system properties:
 * <pre>
 *   mvn test -pl DBAccess \
 *     -Dimpala.test.jdbc.url="jdbc:impala://host:21050/default;UseNativeQuery=1" \
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

        // Skip if the host is not reachable (connect via Hive driver)
        String hiveUrl = toHiveJdbcUrl(jdbcUrl);
        try (Connection c = DriverManager.getConnection(hiveUrl, jdbcUser, jdbcPassword)) {
            assumeTrue(c != null, "Skipping: getConnection() returned null");
        } catch (SQLException e) {
            assumeTrue(false,
                    "Skipping: could not connect to Impala (" + e.getMessage() + ")");
        }
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

    /**
     * Verifies that the Hive JDBC driver can execute a CTE query with {@code ?}
     * parameters via {@link PreparedStatement} against Impala.
     */
    @Test
    void preparedStatement_withParams_canExecuteCTE() throws Exception {
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
