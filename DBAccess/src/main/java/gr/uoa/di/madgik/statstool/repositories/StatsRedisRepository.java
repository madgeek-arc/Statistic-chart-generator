package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.Result;

import org.apache.log4j.Logger;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Repository
public class StatsRedisRepository {
    private RedisTemplate<String, String> redisTemplate;
    private HashOperations<String, String, String> jedis;

    private Logger log = Logger.getLogger(this.getClass());

    public StatsRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.jedis = redisTemplate.opsForHash();
    }

    public Result get(String fullSqlQuery) {
        Result result = null;
        try {
            String redisResponse = jedis.get(MD5(fullSqlQuery), "result");
            if(redisResponse != null) {
                result = new ObjectMapper().readValue(redisResponse, Result.class);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return result;
    }

    public List<String> getValues(String fullSqlQuery) {
        List<String> result = null;
        try {
            String redisResponse = jedis.get(MD5(fullSqlQuery), "result");
            if(redisResponse != null) {
                result = new ObjectMapper().readValue(redisResponse, new ObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return result;
    }

    public void save(String fullSqlQuery, Object result) {
        try {
            String key = MD5(fullSqlQuery);
            jedis.put(key, "persistence", "false");
            jedis.put(key, "query", fullSqlQuery);
            jedis.put(key, "result", new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private static String MD5(String string) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            //e.printStackTrace();
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
