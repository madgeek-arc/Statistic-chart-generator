package gr.uoa.di.madgik.statstool.db;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.query.Query;

public class DBAccess{

    private final Mapper mapper = new Mapper();

    private final String dbUrl = "jdbc:postgresql://vatopedi.di.uoa.gr:5432/stats?autoReconnect=true";
    private final String username = "sqoop";
    private final String password = "sqoop";


    public List<Result> query(List<Query> queryList) {
        List<Result> results = new ArrayList<>();
        try {
            Class.forName("org.postgresql.Driver");
            Connection connection = DriverManager.getConnection(dbUrl, username, password);

            for(Query query : queryList) {
                List<Object> parameters = new ArrayList<>();
                String sql_query = mapper.map(query, parameters);
                System.out.println(sql_query);
                PreparedStatement st = connection.prepareStatement(sql_query);
                int count = 1;
                for(Object param : parameters) {
                    System.out.println(param);
                    st.setObject(count, param);
                    count++;
                }
                ResultSet rs = st.executeQuery();
                int columnCount = rs.getMetaData().getColumnCount();
                Result result = new Result();
                while(rs.next()) {
                    ArrayList<String> row = new ArrayList<>();
                    for(int i = 1; i <= columnCount; i++) {
                        row.add(rs.getString(i));
                    }
                    result.addRow(row);
                }
                rs.close();
                st.close();
                results.add(result);
            }
            connection.close();
        } catch (SQLException | ClassNotFoundException e) {
            return null;
        }
        return results;
    }

    public List<Result> queryTest(List<Query> queryList) {
        List<Result> customResult = new ArrayList<>();
        ObjectMapper objectMapper  = new ObjectMapper();
        try {
            /*
            ArrayList<String> row= new ArrayList<String>();
            row.add("EPLANET");
            row.add("598");
            Result result = new Result();
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            */
            Result result = objectMapper.readValue(getClass().getClassLoader().getResource("result1.json"), Result.class);
            customResult.add(result);
            result = objectMapper.readValue(getClass().getClassLoader().getResource("result2.json"), Result.class);
            customResult.add(result);
            result = objectMapper.readValue(getClass().getClassLoader().getResource("result3.json"), Result.class);
            customResult.add(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Result> results = new ArrayList<>();
        int count = 0;
        for(Query query: queryList) {
            results.add(customResult.get(count % customResult.size()));
            count++;
        }
        return results;
    }
}
