package gr.uoa.di.madgik.statstool.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CacheServiceImpl implements CacheService {

    public static String SHADOW_STATS_NUMBERS = "SHADOW_STATS_NUMBERS";
    public static String STATS_NUMBERS = "STATS_NUMBERS";

    @Autowired
    private NamedQueryRepository namedQueryRepository;

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private StatsRedisRepository redisRepository;

    @Autowired
    private ExecutorService executorService;

    @Value("${statstool.cache.update.entries:5000}")
    private int numberLimit;
    @Value("${statstool.cache.update.seconds:10800}")
    private int timeLimit;

    private final RedisTemplate<String, String> redisTemplate;

    private final HashOperations<String, String, String> jedis;

    private final Logger log = LogManager.getLogger(this.getClass());

    private Boolean updating = false;

    public CacheServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.jedis = redisTemplate.opsForHash();
    }

    @Override
    public void updateCache() {

        synchronized (updating) {
            if (!updating) {
                updating = true;
                new Thread(() -> {
                    doUpdateCache();
                    this.updating=false;
                }).start();
            } else
                throw new IllegalStateException("Cache is already being updated. Please, come back later");
        }

    }

    @Override
    public void promoteCache() {
        this.doPromoteCache();
    }

    private void doUpdateCache() {
        log.info("Starting cache update");
        List<CacheEntry> entries = redisRepository.getEntries();

        entries.sort(new EntriesComparator());

        AtomicInteger i = new AtomicInteger();
        long startTime = new Date().getTime();

        entries.parallelStream().forEach(entry -> {
            try {

                if (i.get() < numberLimit && new Date().getTime() < startTime + timeLimit*1000) {
                    i.getAndIncrement();
                    log.debug(i.get() + ". Updating entry " + entry.getKey() + "(" + entry.getQuery().getDbId() + ") with query " + entry.getQuery());

                    entry.setShadowResult(statsRepository.executeQuery(entry.getQuery().getQuery(), entry.getQuery().getParameters(), entry.getQuery().getDbId().replace("public", "shadow")));
                } else {
                    log.info("time or # of queries limits exceeded. Invalidating entry " + entry.getKey());

                    entry.setShadowResult(null);
                }

                redisRepository.storeEntry(entry);
            } catch (JsonProcessingException e) {
                log.error("Error storing cache entry" ,e);
            } catch (Exception e) {
                log.error("Error updating entry " + entry, e);
                redisRepository.deleteEntry(entry.getKey());
            }
        });

        log.info("Finished cache update!");
    }

    private void doPromoteCache() {
        log.info("Promoting shadow cache values to public");

        List<CacheEntry> entries = redisRepository.getEntries();

        entries.forEach(entry -> {
            if (entry.getShadowResult() != null) {
                entry.setResult(entry.getShadowResult());
            } else {
                entry.setResult(null);
            }

            entry.setSessionHits(0);
            entry.setShadowResult(null);
            entry.setUpdated(new Date());

            try {
                redisRepository.storeEntry(entry);
            } catch (JsonProcessingException e) {
                log.error("Error updating cache entry", e);
            }
        });
    }
}

class EntriesComparator implements Comparator<CacheEntry> {

    @Override
    public int compare(CacheEntry o1, CacheEntry o2) {
        if (o1.isPinned() && !o2.isPinned())
            return -1;
        else if (!o1.isPinned() && o2.isPinned())
            return 1;

        if (o1.getSessionHits() != o2.getSessionHits())
            return o1.getSessionHits() > o2.getSessionHits()?-1:1;

        if (o1.getTotalHits() != o2.getTotalHits())
            return o1.getTotalHits() > o2.getTotalHits()?-1:1;

        return 0;
    }
}