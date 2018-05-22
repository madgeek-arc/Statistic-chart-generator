package gr.uoa.di.madgik.statstool.repositories;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import gr.uoa.di.madgik.statstool.domain.Result;

@Repository
public class StatsRepository {

    private DataSource dataSource;

    public StatsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Result executeQuery(String query, List<Object> parameters) {
        Result result = new Result();
        try {
            Connection connection = dataSource.getConnection();

            PreparedStatement st = connection.prepareStatement(query);
            int count = 1;
            for(Object param : parameters) {
                st.setObject(count, param);
                count++;
            }

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
            e.printStackTrace();
            return null;
        }
    }

    public List<String> executeFieldQuery(String query, String parameter) {
        List<String> result = new ArrayList<>();
        try {
            Connection connection = dataSource.getConnection();

            PreparedStatement st = connection.prepareStatement(query);
            if(!parameter.equals("")) {
                st.setString(1, parameter);
            }

            ResultSet rs = st.executeQuery();
            while(rs.next()) {
                if(rs.getString(1) != null) {
                    result.add(rs.getString(1));
                }
            }

            rs.close();
            st.close();
            connection.close();

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getFullFieldsQuery(String query, String parameter) {
        try {
            Connection connection = dataSource.getConnection();
            PreparedStatement st = connection.prepareStatement(query);
            if(!parameter.equals("")) {
                st.setString(1, parameter);
            }
            String fullQuery = st.toString();
            fullQuery = fullQuery.substring(fullQuery.indexOf("SELECT"));
            st.close();
            connection.close();
            return fullQuery;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getFullQuery(String query, List<Object> parameters) {
        try {
            Connection connection = dataSource.getConnection();
            PreparedStatement st = connection.prepareStatement(query);
            int count = 1;
            for(Object param : parameters) {
                st.setObject(count, param);
                count++;
            }
            String fullQuery = st.toString();
            fullQuery = fullQuery.substring(fullQuery.indexOf("SELECT"));
            st.close();
            connection.close();
            return fullQuery;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

}
