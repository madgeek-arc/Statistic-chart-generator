package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;

import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import org.apache.log4j.Logger;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class StatsRedisRepository {
    private final HashOperations<String, String, String> jedis;
    private final RedisTemplate<String, String> redisTemplate;

    private Logger log = Logger.getLogger(this.getClass());

    public StatsRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.jedis = redisTemplate.opsForHash();
    }

    public static String getCacheKey(String query, List<Object> parameters) throws NoSuchAlgorithmException {
        return getCacheKey(new QueryWithParameters(query, parameters));
    }

    public static String getCacheKey(QueryWithParameters query) throws NoSuchAlgorithmException {
        return MD5(query.toString());
    }

    public boolean exists(String key) throws RedisException {
        try {
            return jedis.hasKey(key, "result") && (jedis.get(key, "result") != null && !jedis.get(key, "result").equals("null"));
        } catch (Exception e) {
            throw new RedisException(e);
        }
    }

    public Result get(String key) throws RedisException {
        if (!exists(key))
            throw new RedisException("Key " + key + " does not exist");

        try {
            jedis.increment(key, "totalHits", 1);
            jedis.increment(key, "sessionHits", 1);

            return getEntry(key).getResult();
        } catch (Exception e) {
            throw new RedisException(e);
        }
    }

    public String save(QueryWithParameters fullSqlQuery, Result result) throws RedisException {
        try {
            String key = getCacheKey(fullSqlQuery);

            if (exists(key))
                throw new RedisException("Entry for query " + fullSqlQuery + " (" + key + ") already exists!");

            storeEntry(new CacheEntry(key, fullSqlQuery, result));

            return key;
        } catch (Exception e) {
            throw new RedisException(e);
        }
    }

    public CacheEntry getEntry(String key) throws IOException {
        CacheEntry entry;
        QueryWithParameters query = new ObjectMapper().readValue(jedis.get(key, "query"), QueryWithParameters.class);
        Result result = new ObjectMapper().readValue(jedis.get(key, "result"), Result.class);

        entry = new CacheEntry(key, query, result);

        if (jedis.get(key, "created") != null)
            entry.setCreated(new Date(Long.parseLong(Objects.requireNonNull(jedis.get(key, "created")))));
        if (jedis.get(key, "updated") != null)
            entry.setUpdated(new Date(Long.parseLong(Objects.requireNonNull(jedis.get(key, "updated")))));
        if (jedis.get(key, "totalHits") != null)
            entry.setTotalHits(Integer.parseInt(Objects.requireNonNull(jedis.get(key, "totalHits"))));
        if (jedis.get(key, "sessionHits") != null)
            entry.setSessionHits(Integer.parseInt(Objects.requireNonNull(jedis.get(key, "sessionHits"))));
        if (jedis.get(key, "pinned") != null)
            entry.setPinned(Boolean.parseBoolean(jedis.get(key, Objects.requireNonNull(jedis.get(key, "pinned")))));
        if (jedis.get(key, "shadow") != null)
            entry.setShadowResult( new ObjectMapper().readValue(jedis.get(key, "shadow"), Result.class));

        return entry;
    }

    public void storeEntry(CacheEntry entry) throws JsonProcessingException {
        String key = entry.getKey();

        log.info("storing entry with result" + entry.getResult());

        jedis.put(key, "query", new ObjectMapper().writeValueAsString(entry.getQuery()));
        jedis.put(key, "result", new ObjectMapper().writeValueAsString(entry.getResult()));
        jedis.put(key, "shadow", new ObjectMapper().writeValueAsString(entry.getShadowResult()));
        jedis.put(key, "created", Long.valueOf(entry.getCreated().getTime()).toString());
        jedis.put(key, "updated", Long.valueOf(entry.getUpdated().getTime()).toString());
        jedis.put(key, "totalHits", Integer.valueOf(entry.getTotalHits()).toString());
        jedis.put(key, "sessionHits", Integer.valueOf(entry.getSessionHits()).toString());
        jedis.put(key, "pinned", Boolean.valueOf(entry.isPinned()).toString());
    }

    public List<CacheEntry> getEntries() {
        Set<String> keys = this.redisTemplate.keys("*");

        assert keys != null;

        return keys.stream().filter(key -> {
            return !(key.equals("SHADOW_STATS_NUMBERS") || key.equals("STATS_NUMBERS"));
        }).map(key -> {
            try {
                return getEntry(key);
            } catch (Exception e) {
                log.error(e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void deleteEntry(String key) {
        redisTemplate.delete(key);
    }

    public void fixEntries() {
        log.info("fixing cache entries");

        Set<String> keys = this.redisTemplate.keys("*");

        assert keys != null;

        keys.stream().filter(key -> {
            return !(key.equals("SHADOW_STATS_NUMBERS") || key.equals("STATS_NUMBERS"));
        }).forEach(key -> {
            try {
                log.info("trying key " + key + " with query " +  jedis.get(key, "query"));

                if (jedis.get(key, "query") == null) {
                    log.info("NULL query! Bye bye!!!");
                    redisTemplate.delete(key);
                    return;
                }
                new ObjectMapper().readValue(jedis.get(key, "query"), QueryWithParameters.class);
            } catch (IOException e) {
                log.info("Query seems invalid!");

                try {
                    String q = jedis.get(key, "query");
                    QueryWithParameters query = fixEntryQuery(q);

                    log.info("fixed query:" + query);
                    String newKey = getCacheKey(query);
                    Result result = new ObjectMapper().readValue(jedis.get(key, "result"), Result.class);

                    log.info("fixing query with result " + result);

                    CacheEntry entry = new CacheEntry(newKey, query, result);

                    if (jedis.get(key, "created") != null)
                        entry.setCreated(new Date(Long.parseLong(Objects.requireNonNull(jedis.get(key, "created")))));
                    if (jedis.get(key, "updated") != null)
                        entry.setUpdated(new Date(Long.parseLong(Objects.requireNonNull(jedis.get(key, "updated")))));
                    if (jedis.get(key, "totalHits") != null)
                        entry.setTotalHits(Integer.parseInt(Objects.requireNonNull(jedis.get(key, "totalHits"))));
                    if (jedis.get(key, "sessionHits") != null)
                        entry.setSessionHits(Integer.parseInt(Objects.requireNonNull(jedis.get(key, "sessionHits"))));
                    if (jedis.get(key, "pinned") != null)
                        entry.setPinned(Boolean.parseBoolean(jedis.get(key, Objects.requireNonNull(jedis.get(key, "pinned")))));
                    if (jedis.get(key, "shadow") != null)
                        entry.setShadowResult( new ObjectMapper().readValue(jedis.get(key, "shadow"), Result.class));

                    log.info("storing new entry: " + entry);
                    storeEntry(entry);
                    redisTemplate.delete(key);

                } catch (Exception ee) {}
            }
        });
    }

    private QueryWithParameters fixEntryQuery (String query) {
        String[] parts = query.replaceAll(";;", ";").split(";");

        query = parts[0];
        List<Object> params = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String v = parts[i];

            if (v == null || v.trim().isEmpty() )
                continue;

            try {
                params.add(Integer.parseInt(v));
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

        return new QueryWithParameters(query, params);
    }

    private static String MD5(String string) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");

        md.update(string.getBytes());

        byte[] byteData = md.digest();
        StringBuilder sb = new StringBuilder();

        for (byte aByteData : byteData) {
            sb.append(Integer.toString((aByteData & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
    }
}