package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.TimedResult;
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

    private final Map<QueryWithParameters, Future<TimedResult>> tasks = new HashMap<>();

    public StatsRepository(DataSource dataSource, ExecutorService executorService) {
        this.dataSource = dataSource;
        this.executorService = executorService;
    }

    public TimedResult executeQuery(String query, List<Object> parameters, String dbId) throws Exception {
        QueryWithParameters q = new QueryWithParameters(query, parameters, dbId);
        Future<TimedResult> future;
        long submitTime = System.currentTimeMillis();

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
            TimedResult tr = future.get();
            long totalElapsed = System.currentTimeMillis() - submitTime;
            int queueTimeMs = (int) Math.max(0, totalElapsed - tr.execTimeMs);
            return new TimedResult(tr.result, tr.execTimeMs, queueTimeMs);
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

    public class ResultCallable implements Callable<TimedResult> {
        private final QueryWithParameters query;

        public ResultCallable(QueryWithParameters q) {
            this.query = q;
        }

        @Override
        public TimedResult call() throws Exception {
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

            long execStart = System.currentTimeMillis();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement st = connection.prepareStatement(sql)) {
                int index = 1;
                if (params != null) {
                    for (Object param : params) {
                        st.setObject(index++, param);
                    }
                }
                try (ResultSet rs = st.executeQuery()) {
                    Result result = readResult(rs);
                    int execTimeMs = (int) (System.currentTimeMillis() - execStart);
                    return new TimedResult(result, execTimeMs, 0);
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

}
