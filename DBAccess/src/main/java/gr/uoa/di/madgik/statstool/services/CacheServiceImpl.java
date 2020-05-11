package gr.uoa.di.madgik.statstool.services;

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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CacheServiceImpl implements CacheService {

    public static String SHADOW_STATS_NUMBERS = "SHADOW_STATS_NUMBERS";
    public static String STATS_NUMBERS = "STATS_NUMBERS";

    @Autowired
    private NamedQueryRepository namedQueryRepository;

    @Autowired
    private StatsRepository statsRepository;

    private RedisTemplate<String, String> redisTemplate;

    private HashOperations<String, String, String> jedis;

    private final Logger log = Logger.getLogger(this.getClass());

    private ExecutorService executorService = Executors.newFixedThreadPool(3);

    public CacheServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.jedis = redisTemplate.opsForHash();
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
