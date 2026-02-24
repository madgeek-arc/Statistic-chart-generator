package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Repository
public class StatsRepository {

    private final DataSource dataSource;

    private final ExecutorService executorService;

    private final Logger log = LogManager.getLogger(this.getClass());

    private final Map<QueryWithParameters, Future<Result>> tasks = new HashMap<>();

    public StatsRepository(DataSource dataSource, ExecutorService executorService) {
        this.dataSource = dataSource;
        this.executorService = executorService;
    }

    public Result executeQuery(String query, List<Object> parameters, String dbId) throws Exception {
        QueryWithParameters q = new QueryWithParameters(query, parameters, dbId);
        Future<Result> future;

        synchronized (tasks) {
            future = tasks.get(q);

            if (future == null) {
                log.debug("Query " + q + " was not in queue. Submitting");
                future = executorService.submit(new ResultCallable(q));
                tasks.put(q, future);
            } else {
                log.debug("query " + q + " was already submitted. Waiting for completion");
            }

            log.info("size of queue: " + tasks.size());
        }

        try {

            return future.get();
        } finally {
            synchronized (tasks) {
                tasks.remove(q);
                log.info("size of queue: " + tasks.size());
            }
        }
    }

    private static int countPlaceholders(String sql) {
        boolean inSingle = false;
        boolean inDouble = false;
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                // handle doubled single quotes inside single-quoted strings
                inSingle = !inSingle;
                // if next is also quote, stay inside string
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    // skip the escaped quote
                    i++;
                }
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (!inSingle && !inDouble && c == '?') {
                count++;
            }
        }
        return count;
    }

    public class ResultCallable implements Callable<Result> {
        private final QueryWithParameters query;

        public ResultCallable(QueryWithParameters q) {
            this.query = q;
        }

        @Override
        public Result call() throws Exception {
            DatasourceContext.setContext(query.getDbId());

            String sql = query.getQuery();
            List<Object> params = query.getParameters();

            // Validate placeholder and parameter counts BEFORE acquiring a connection
            int placeholderCount = countPlaceholders(sql);
            int paramCount = (params == null) ? 0 : params.size();
            if (placeholderCount != paramCount) {
                throw new IllegalArgumentException("Placeholder count (" + placeholderCount + ") does not match parameter count (" + paramCount + ")");
            }
            // Validate no null parameters allowed BEFORE acquiring a connection
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    if (params.get(i) == null) {
                        throw new IllegalArgumentException("Null parameter at index " + (i + 1) + " is not allowed");
                    }
                }
            }

            try (Connection connection = dataSource.getConnection()) {
                try {
                    try (PreparedStatement st = connection.prepareStatement(sql)) {
                        int index = 1;
                        if (params != null) {
                            for (Object param : params) {
                                st.setObject(index++, param);
                            }
                        }
                        try (ResultSet rs = st.executeQuery()) {
                            return readResult(rs);
                        }
                    }
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("11420")) {
                        // Simba JDBC (Impala) cannot resolve parameter metadata for ? inside CTEs.
                        // Fall back to a plain Statement with parameters safely inlined as SQL literals.
                        String inlinedSql = inlineParameters(sql, params);
                        try (Statement st = connection.createStatement();
                             ResultSet rs = st.executeQuery(inlinedSql)) {
                            return readResult(rs);
                        }
                    }
                    throw e;
                }
            }
        }
    }

    private static Result readResult(ResultSet rs) throws SQLException {
        Result result = new Result();
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            ArrayList<Object> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                String stringResult = rs.getString(i);
                row.add("null".equals(stringResult) ? null : stringResult);
            }
            result.addRow(row);
        }
        return result;
    }

    /**
     * Replaces each {@code ?} placeholder in {@code sql} with the corresponding
     * parameter value rendered as a SQL literal.  String literals in the SQL
     * (single- or double-quoted) are skipped so that a {@code ?} that appears
     * inside a quoted string is never touched.
     */
    static String inlineParameters(String sql, List<Object> params) {
        if (params == null || params.isEmpty()) return sql;
        StringBuilder result = new StringBuilder();
        int paramIdx = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                result.append(c);
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    result.append('\'');
                    i++;
                }
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                result.append(c);
                continue;
            }
            if (!inSingle && !inDouble && c == '?') {
                result.append(toSqlLiteral(params.get(paramIdx++)));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Renders a Java object as a SQL literal safe for direct embedding in a
     * statement string.  Numbers and booleans are written verbatim; everything
     * else is single-quoted with internal single quotes escaped by doubling.
     */
    static String toSqlLiteral(Object param) {
        if (param instanceof Number || param instanceof Boolean) {
            return param.toString();
        }
        return "'" + param.toString().replace("'", "''") + "'";
    }
}
