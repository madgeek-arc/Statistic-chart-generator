package gr.uoa.di.madgik.statstool.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
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

    private final int numberLimit = 1000;
    private final int timeLimit = 3600;

    private final RedisTemplate<String, String> redisTemplate;

    private final HashOperations<String, String, String> jedis;

    private final Logger log = Logger.getLogger(this.getClass());

    //private ExecutorService executorService = Executors.newFixedThreadPool(3);

    public CacheServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.jedis = redisTemplate.opsForHash();
    }

    @Override
    public void updateCache() {
        log.info("Starting cache update");
        List<CacheEntry> entries = redisRepository.getEntries();

        entries.sort(new EntriesComparator());

        AtomicInteger i = new AtomicInteger();
        long startTime = new Date().getTime();

        entries.forEach(entry -> {
            try {

                if (i.get() < numberLimit && new Date().getTime() < startTime + timeLimit*1000) {
                    log.info(i.getAndIncrement() + ". Updating entry " + entry.getKey() + " with query " + entry.getQuery());

                    entry.setShadowResult(statsRepository.executeQuery(entry.getQuery().getQuery(), entry.getQuery().getParameters()));
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

    @Override
    public void fixEntries() {
        redisRepository.fixEntries();
    }

    @Override
    public void promoteCache() {
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


    @Override
    public void calculateNumbers() throws StatsServiceException {
        try {
            Properties queries = namedQueryRepository.getNumberQueries();

            for (Object queryName:queries.keySet()) {
                String query = queries.getProperty((String) queryName);

                executorService.submit(new Updater(query, (String) queryName));
            }
        } catch (Exception e) {
            throw new StatsServiceException(e);
        }
    }

    @Override
    public void promoteNumbers() {
        redisTemplate.rename(SHADOW_STATS_NUMBERS, STATS_NUMBERS);
    }

    class Updater implements Runnable {

        private final String query;
        private final String queryName;

        public Updater(String query, String queryName) {
            this.query = query;
            this.queryName = queryName;
        }

        @Override
        public void run() {
            try {
                String result = statsRepository.executeNumberQuery(query);

                if (result != null)
                    jedis.put(SHADOW_STATS_NUMBERS, queryName, result);

                log.info("updating cache number:" + queryName + ": " + result);
            } catch (Exception e){
                log.error("Error updating number:" + queryName, e);
            }
        }
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