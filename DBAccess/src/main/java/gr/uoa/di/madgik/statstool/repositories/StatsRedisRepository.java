package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;

import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class StatsRedisRepository implements StatsCache {
    private final HashOperations<String, String, String> jedis;
    private final RedisTemplate<String, String> redisTemplate;

    private final Logger log = LogManager.getLogger(this.getClass());

    @Value("${statstool.cache.enabled:true}")
    private boolean enableCache;

    public StatsRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.jedis = redisTemplate.opsForHash();
    }

    @Override
    public boolean exists(String key) throws RedisException {
        if (!enableCache)
            return false;

        try {
            return jedis.hasKey(key, "result") && (jedis.get(key, "result") != null && !jedis.get(key, "result").equals("null"));
        } catch (Exception e) {
            throw new RedisException(e);
        }
    }

    @Override
    public Result get(String key) throws RedisException {
        if (!enableCache)
            throw new RuntimeException("Cache is not enabled!");

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

    @Override
    public String save(QueryWithParameters fullSqlQuery, Result result) throws RedisException {

        try {
            String key = StatsCache.getCacheKey(fullSqlQuery);

            if (!enableCache)
                log.debug("Cache is not enabled. Noop!");
            else
                storeEntry(new CacheEntry(key, fullSqlQuery, result));

            return key;
        } catch (Exception e) {
            throw new RedisException(e);
        }
    }

    private CacheEntry getEntry(String key) throws IOException {
        CacheEntry entry;
        QueryWithParameters query = new ObjectMapper().readValue(jedis.get(key, "query"), QueryWithParameters.class);
        Result result = new ObjectMapper().readValue(jedis.get(key, "result"), Result.class);
        String dbId = jedis.get(key, "dbId");

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

    @Override
    public void storeEntry(CacheEntry entry) throws JsonProcessingException {

        if (!enableCache) {
            log.debug("Cache is not enabled. Noop");
            return;
        }

        String key = entry.getKey();

        log.debug("storing entry with result" + entry.getResult());

        jedis.put(key, "query", new ObjectMapper().writeValueAsString(entry.getQuery()));
        jedis.put(key, "result", new ObjectMapper().writeValueAsString(entry.getResult()));
        jedis.put(key, "shadow", new ObjectMapper().writeValueAsString(entry.getShadowResult()));
        jedis.put(key, "created", Long.valueOf(entry.getCreated().getTime()).toString());
        jedis.put(key, "updated", Long.valueOf(entry.getUpdated().getTime()).toString());
        jedis.put(key, "totalHits", Integer.valueOf(entry.getTotalHits()).toString());
        jedis.put(key, "sessionHits", Integer.valueOf(entry.getSessionHits()).toString());
        jedis.put(key, "pinned", Boolean.valueOf(entry.isPinned()).toString());
    }

    @Override
    public List<CacheEntry> getEntries() {

        if (!enableCache) {
            log.debug("Cache is not enabled. Returning empty list");

            return Collections.emptyList();
        }

        Set<String> keys = this.redisTemplate.keys("*");

        assert keys != null;

        return keys.stream().filter(key -> {
            return !(key.equals("SHADOW_STATS_NUMBERS") || key.equals("STATS_NUMBERS"));
        }).map(key -> {
            try {
                return getEntry(key);
            } catch (Exception e) {
                log.error("Error getting entry", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void deleteEntry(String key) {
        if (!enableCache) {
            log.debug("Cache is not enabled. Noop");
            return;
        }

        redisTemplate.delete(key);
    }
}