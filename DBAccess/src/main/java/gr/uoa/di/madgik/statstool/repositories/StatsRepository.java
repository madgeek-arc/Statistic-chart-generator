package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
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

    private static void bindParameter(PreparedStatement st, int index, Object value) throws SQLException {
        if (value == null) {
            throw new IllegalArgumentException("Null parameter at index " + index + " is not allowed");
        }
        if (value instanceof String) {
            st.setString(index, (String) value);
        } else if (value instanceof Integer) {
            st.setInt(index, (Integer) value);
        } else if (value instanceof Long) {
            st.setLong(index, (Long) value);
        } else if (value instanceof Double) {
            st.setDouble(index, (Double) value);
        } else if (value instanceof Float) {
            st.setFloat(index, (Float) value);
        } else if (value instanceof BigDecimal) {
            st.setBigDecimal(index, (BigDecimal) value);
        } else if (value instanceof Boolean) {
            st.setBoolean(index, (Boolean) value);
        } else if (value instanceof java.sql.Date) {
            st.setDate(index, (java.sql.Date) value);
        } else if (value instanceof java.sql.Timestamp) {
            st.setTimestamp(index, (java.sql.Timestamp) value);
        } else if (value instanceof java.util.Date) {
            // prefer Timestamp for util.Date to preserve time component
            st.setTimestamp(index, new java.sql.Timestamp(((java.util.Date) value).getTime()));
        } else if (value instanceof Short) {
            st.setShort(index, (Short) value);
        } else if (value instanceof Byte) {
            st.setByte(index, (Byte) value);
        } else {
            // Fallback: concrete non-null object; let driver handle it
            st.setObject(index, value);
        }
    }

    public class ResultCallable implements Callable<Result> {
        private final QueryWithParameters query;

        public ResultCallable(QueryWithParameters q) {
            this.query = q;
        }

        @Override
        public Result call() throws Exception {
            Result result = new Result();

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
                PreparedStatement st = connection.prepareStatement(sql);
                int index = 1;
                if (params != null) {
                    for (Object param : params) {
                        bindParameter(st, index++, param);
                    }
                }

                ResultSet rs = st.executeQuery();
                int columnCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    ArrayList<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String stringResult = rs.getString(i);
                        if ("null".equals(stringResult))
                            row.add(null);
                        else
                            row.add(stringResult);
                    }
                    result.addRow(row);
                }

                rs.close();
                st.close();
                connection.close();
                return result;
            }
        }
    }
}
