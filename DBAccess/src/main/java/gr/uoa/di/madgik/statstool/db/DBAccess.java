package gr.uoa.di.madgik.statstool.db;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.query.Query;
import redis.clients.jedis.Jedis;

public class DBAccess{

    private final Mapper mapper = new Mapper();
    Jedis jedis = new Jedis("vatopedi.di.uoa.gr", 6379);

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

                String redisKey = MD5(st.toString());
                String redisResponse = jedis.hget(redisKey, "result");
                if(redisResponse != null) {
                    results.add(new ObjectMapper().readValue(redisResponse, Result.class));
                    st.close();
                    continue;
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

                jedis.hset(redisKey, "persistent", "false");
                jedis.hset(redisKey, "query", st.toString());
                jedis.hset(redisKey, "result", new ObjectMapper().writeValueAsString(result));

                rs.close();
                st.close();
                results.add(result);
            }
            connection.close();
        } catch (Exception e) {
            return null;
        }
        return results;
    }

    private static String MD5(String string) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        md.update(string.getBytes());

        byte byteData[] = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByteData : byteData) {
            sb.append(Integer.toString((aByteData & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
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
