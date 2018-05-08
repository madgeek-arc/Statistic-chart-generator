package gr.uoa.di.madgik.statstool.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.domain.Query;
import redis.clients.jedis.Jedis;

public class DBAccess{

    private final Mapper mapper = new Mapper();
    private final Jedis jedis = new Jedis("vatopedi.di.uoa.gr", 6379);

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
                PreparedStatement st = connection.prepareStatement(mapper.map(query, parameters));
                int count = 1;
                for(Object param : parameters) {
                    st.setObject(count, param);
                    count++;
                }

                String redisKey = MD5(st.toString());
                Result result = checkRedis(redisKey);
                if(result != null) {
                    results.add(result);
                    st.close();
                    continue;
                }

                ResultSet rs = st.executeQuery();
                int columnCount = rs.getMetaData().getColumnCount();
                result = new Result();
                while(rs.next()) {
                    ArrayList<String> row = new ArrayList<>();
                    for(int i = 1; i <= columnCount; i++) {
                        row.add(rs.getString(i));
                    }
                    result.addRow(row);
                }
                addToRedis(redisKey, st.toString(), result);

                rs.close();
                st.close();
                results.add(result);
            }
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return results;
    }

    private Result checkRedis(String key) {
        Result result = null;
        try {
            String redisResponse = jedis.hget(key, "result");
            if(redisResponse != null) {
                result = new ObjectMapper().readValue(redisResponse, Result.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void addToRedis(String key, String query, Result result) {
        try {
            jedis.hset(key, "persistent", "false");
            jedis.hset(key, "query", query);
            jedis.hset(key, "result", new ObjectMapper().writeValueAsString(result));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
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
}
