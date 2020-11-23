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

    public CacheServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.jedis = redisTemplate.opsForHash();
    }

    @Override
    public void updateCache() {
        this.doUpdateCache();
        this.doPromoteCache();
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
                    log.debug(i.get() + ". Updating entry " + entry.getKey() + " with query " + entry.getQuery());

                    entry.setShadowResult(statsRepository.executeQuery(entry.getQuery().getQuery(), entry.getQuery().getParameters()));
                } else {
                    log.debug("time or # of queries limits exceeded. Invalidating entry " + entry.getKey());

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
