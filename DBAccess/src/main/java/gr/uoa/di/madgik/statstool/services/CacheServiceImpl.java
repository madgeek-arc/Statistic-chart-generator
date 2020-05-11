package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Service;

import javax.print.attribute.standard.PrinterMessageFromOperator;
import java.io.IOException;
import java.util.Properties;

@Service
public class CacheServiceImpl implements CacheService {

    public static String SHADOW_STATS_NUMBERS = "SHADOW_STATS_NUMBERS";
    public static String STATS_NUMBERS = "STATS_NUMBERS";

    @Autowired
    private NamedQueryRepository namedQueryRepository;

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private HashOperations<String, String, String> jedis;

    private final Logger log = Logger.getLogger(this.getClass());

    @Override
    public void calculateNumbers() throws StatsServiceException {
        try {
            Properties queries = namedQueryRepository.getNumberQueries();

            for (Object queryName:queries.keySet()) {
                String query = queries.getProperty((String) queryName);

                try {
                    String result = statsRepository.executeNumberQuery(query);

                    jedis.put(SHADOW_STATS_NUMBERS, (String) queryName, result);

                    log.info("updating cache number:" + queryName + ": " + result);
                } catch (Exception e){
                    log.error("Error updating number:" + queryName, e);
                }
            }
        } catch (Exception e) {
            throw new StatsServiceException(e);
        }
    }

    @Override
    public void promoteNumbers() {

    }
}
