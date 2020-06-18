package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.print.attribute.standard.PrinterMessageFromOperator;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

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

    private RedisTemplate<String, String> redisTemplate;

    private HashOperations<String, String, String> jedis;

    private final Logger log = Logger.getLogger(this.getClass());

    private ExecutorService executorService = Executors.newFixedThreadPool(3);

    public CacheServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.jedis = redisTemplate.opsForHash();
    }

    @Override
    public void updateCache() {
        log.info("Starting cache update");
        Set<String> keys = redisTemplate.keys("*");
        final int parallelism = 3;
        ForkJoinPool forkJoinPool = null;

        try {
            forkJoinPool = new ForkJoinPool(parallelism);

            forkJoinPool.submit(() -> {
                keys.parallelStream().forEach(key -> {
                    String query = null;

                    log.info("Updating key " + key);

                    if (!key.equals(STATS_NUMBERS)) {
                        try {
                            query = (String) redisTemplate.opsForHash().get(key, "query");

                            log.info("Updating key " + key + " with query " + query);

                            Result res = statsRepository.executeQuery(query, Collections.emptyList());

                            redisRepository.save(query, res);
                        } catch (Exception e) {
                            log.error("Error updating query: key: " + key + ", query: " + query, e);
                        }
                    }
                });
            });

        } catch ( Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }

        log.info("Finished cache update!");
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

        private String query;
        private String queryName;

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
