package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import gr.uoa.di.madgik.statstool.domain.Result;

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

    public Result executeQuery(String query, List<Object> parameters) throws Exception {
        QueryWithParameters q = new QueryWithParameters(query, parameters);
        Future<Result> future = null;

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

    public String executeNumberQuery(String query) {
        String result = null;
        Connection connection = null;

        try {
            connection = dataSource.getConnection();

            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(query);

            if (rs.next()){
                Object o = rs.getObject(1);

                result = o!=null?o.toString():null;
            }

            rs.close();
            st.close();
            connection.close();
        } catch (Exception e) {
            log.error("Error executing query", e);
            return null;
        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (Exception e) {
            }
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

            Connection connection = null;
            try {
                connection = dataSource.getConnection();

                PreparedStatement st = connection.prepareStatement(query.getQuery());
                int count = 1;
                if (query.getParameters() != null)
                    for (Object param : query.getParameters())
                        st.setObject(count++, param);

                ResultSet rs = st.executeQuery();
                int columnCount = rs.getMetaData().getColumnCount();
                while(rs.next()) {
                    ArrayList<String> row = new ArrayList<>();
                    for(int i = 1; i <= columnCount; i++) {
                        row.add(rs.getString(i));
                    }
                    result.addRow(row);
                }

                rs.close();
                st.close();
                connection.close();
                return result;
            } catch (Exception e) {
                throw e;
            } finally {
                try {
                    if (connection != null)
                        connection.close();
                } catch (Exception e) {}
            }
        }
    }
}
