package gr.uoa.di.madgik.statstool.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.TimedResult;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CacheServiceImpl implements CacheService {

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private StatsCache statsCache;

    @Autowired
    private NlSqlCache nlSqlCache;

    @Autowired
    private NlOptionsCache nlOptionsCache;

    @Value("${statstool.cache.update.entries:5000}")
    private int numberLimit;
    @Value("${statstool.cache.update.seconds:10800}")
    private int timeLimit;

    private final Logger log = LogManager.getLogger(this.getClass());

    private Boolean updating = false;

    @Override
    public void updateCache(String profile) {

	log.info("Updating cache for " + (profile!=null?"'"+profile+"'":"all") + " profile(s)");

        synchronized (updating) {
            if (!updating) {
                updating = true;
                new Thread(() -> {
                    doUpdateCache(profile);
                    this.updating=false;
                }).start();
            } else
                throw new IllegalStateException("Cache is already being updated. Please, come back later");
        }

    }

    @Override
    public void promoteCache(String profile) {

	log.info("Promoting cache for " + (profile!=null?"'"+profile+"'":"all") + " profile(s)");
        this.doPromoteCache(profile);
    }

    public void dropCache(String profile) throws Exception {
	log.info("Dropping cache for " + (profile!=null?"'"+profile+"'":"all") + " profile(s)");
        this.statsCache.dropCache(profile);
    }

    public Map<String, Object> getStats() throws Exception {
        return this.statsCache.stats();
    }

    @Override
    public void dropNlCache(String profile) {
        log.info("Dropping NL SQL cache for " + (profile != null ? "'" + profile + "'" : "all") + " profile(s)");
        nlSqlCache.drop(profile);
    }

    @Override
    public void evictNlCache(String profile, String canonicalNl) {
        log.info("Evicting NL SQL cache entry for profile='" + profile + "'");
        nlSqlCache.evict(profile, canonicalNl);
    }

    @Override
    public void dropNlOptionsCache(String library) {
        log.info("Dropping NL options cache for " + (library != null ? "'" + library + "'" : "all") + " library(s)");
        nlOptionsCache.drop(library);
    }

    @Override
    public void evictNlOptionsCache(String library, String canonicalDescription) {
        log.info("Evicting NL options cache entry for library='" + library + "'");
        nlOptionsCache.evict(library, canonicalDescription);
    }

    private void doUpdateCache(String profile) {
        log.info("Starting cache update");
        List<CacheEntry> entries = statsCache.getEntries(profile);

        entries.sort(new EntriesComparator());

        AtomicInteger i = new AtomicInteger();
        long startTime = new Date().getTime();

        entries.parallelStream().forEach(entry -> {
            try {

                if (i.get() < numberLimit && new Date().getTime() < startTime + timeLimit*1000) {
                    i.getAndIncrement();
                    log.debug(i.get() + ". Updating entry " + entry.getKey() + "(" + entry.getQuery().getDbId() + ") with query " + entry.getQuery());

                    TimedResult timedResult = statsRepository.executeQuery(entry.getQuery().getQuery(), entry.getQuery().getParameters(), entry.getQuery().getDbId().replace("public", "shadow"));

                    entry.setShadowResult(timedResult.result);
                    entry.setExecTime(timedResult.execTimeMs);
                    entry.setQueueTime(timedResult.queueTimeMs);
                } else {
                    log.info("time or # of queries limits exceeded. Invalidating entry " + entry.getKey());

                    entry.setShadowResult(null);
                }

                statsCache.storeEntry(entry);
            } catch (JsonProcessingException e) {
                log.error("Error storing cache entry" ,e);
            } catch (Exception e) {
                log.error("Error updating entry " + entry, e);
                statsCache.deleteEntry(entry.getKey());
            }
        });

        log.info("Finished cache update!");
    }

    private void doPromoteCache(String profile) {
        log.info("Promoting shadow cache values to public");

        List<CacheEntry> entries = statsCache.getEntries(profile);

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
                statsCache.storeEntry(entry);
            } catch (Exception e) {
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
