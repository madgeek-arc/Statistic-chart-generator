package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;

import javax.sql.DataSource;

import gr.uoa.di.madgik.statstool.domain.Result;

@Repository
public class StatsRepository {

    private final DataSource dataSource;

    private final Logger log = Logger.getLogger(this.getClass());

    public StatsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Result executeQuery(String query, List<Object> parameters) throws SQLException {
        Result result = new Result();

	    Connection connection = null;
        try {
            connection = dataSource.getConnection();

            PreparedStatement st = connection.prepareStatement(query);
            int count = 1;
            if (parameters != null)
                for (Object param : parameters)
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
            } catch (Exception e) {
            }
        }
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

    public static String getFullQuery(String query, List<Object> parameters) {
	    StringBuilder sb = new StringBuilder();

        sb.append(query);
        for (Object o : parameters)
            sb.append(';').append(o);

        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
//        List<Object> l = new ArrayList<>( );
//
//        l.add("sdsd");
//        l.add(23);
//        l.add(new java.util.Date());
//        l.add(false);
//
//
//
//
//        QueryWithParameters q = new QueryWithParameters("select foof offfo fffofofofo fofof", l);
//        System.out.println(new ObjectMapper().writeValueAsString(q));
//
//        q = new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(q), QueryWithParameters.class);
//
//        System.out.println(q);
//        System.out.println(q.getQuery());
//        q.getParameters().forEach(o->{
//            System.out.println(o + " " + o.getClass());
//        });

//        String qq = "SDfsd fdfg sdfgh dfs g;;sdfsdf;3425;true;" + new Date().getTime() + ";erg";

        Arrays.asList("sdflksjdflskdjf;", "sdfsdfgdslfkgjsdl;;", "sdlkfjsldkfgjsldkfgj", "sdfl;kjsdlfkj;234234;true;blah").forEach(qq -> {
            System.out.println(qq);
            try {
                new ObjectMapper().readValue(qq, QueryWithParameters.class);
            } catch (IOException e) {
                String[] parts = qq.replaceAll(";;", ";").split(";");

                qq = parts[0];
                List<Object> params = new ArrayList<>();

                for (int i = 1; i < parts.length; i++) {
                    String v = parts[i];

                    try {
                        params.add(Integer.parseInt(v));

                        System.out.println(new Date(Integer.parseInt(v)));
                        System.out.println(new Date(Integer.parseInt(v)).getTime());
                        continue;
                    } catch (NumberFormatException e1) {}
                    try {
                        params.add(new Date(Long.parseLong(v)));
                        continue;
                    } catch (NumberFormatException e1) {}
                    try {
                        params.add(Float.parseFloat(v));
                        continue;
                    } catch (NumberFormatException e1) {}

                    if (v.trim().toLowerCase().equals("true") || v.trim().toLowerCase().equals("false")) {
                        params.add(Boolean.parseBoolean(v));
                        continue;
                    }

                    params.add(v);
                }

                System.out.println(new QueryWithParameters(qq, params));

            }
        });
    }
}
