package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    public class ResultCallable implements Callable<Result> {
        private final QueryWithParameters query;

        public ResultCallable(QueryWithParameters q) {
            this.query = q;
        }

        @Override
        public Result call() throws Exception {
            Result result = new Result();

            DatasourceContext.setContext(query.getDbId());

            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement st = connection.prepareStatement(query.getQuery());
                int count = 1;
                if (query.getParameters() != null)
                    for (Object param : query.getParameters())
                        st.setObject(count++, param);

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
