package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import gr.uoa.di.madgik.statstool.domain.Result;

public class StatsRepository {

    private final DataSource dataSource;

    private final ExecutorService executorService;

    private final Logger log = LogManager.getLogger(this.getClass());

    private final Map<QueryWithParameters, Future<Result>> tasks = new HashMap<>();

    public StatsRepository(DataSource dataSource, ExecutorService executorService) {
        this.dataSource = dataSource;
        this.executorService = executorService;
    }

    public Result executeQuery(String query, List<Object> parameters) throws Exception {
        QueryWithParameters q = new QueryWithParameters(query, parameters);
        Future<Result> future;

        synchronized (tasks) {
            future = tasks.get(q);

            if (future == null) {
                log.info("Query " + q + " was not in queue. Submitting");
                future = executorService.submit(new ResultCallable(q));
                tasks.put(q, future);
            } else {
                log.info("query " + q + " was already submitted. Waiting for completion");
            }

            log.info("size of queue: " + tasks.size());
        }

        Result result = future.get();

        synchronized (tasks) {
            tasks.remove(q);
        }

        return result;
    }

    public class ResultCallable implements Callable<Result> {
        private final QueryWithParameters query;

        public ResultCallable(QueryWithParameters q) {
            this.query = q;
        }

        @Override
        public Result call() throws Exception {
            Result result = new Result();

            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement st = connection.prepareStatement(query.getQuery());
                int count = 1;
                if (query.getParameters() != null)
                    for (Object param : query.getParameters())
                        st.setObject(count++, param);

                ResultSet rs = st.executeQuery();
                int columnCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    ArrayList<String> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getString(i));
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
