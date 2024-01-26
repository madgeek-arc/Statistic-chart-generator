package gr.uoa.di.madgik.statstool.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
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

    @Value("${statstool.cache.update.entries:5000}")
    private int numberLimit;
    @Value("${statstool.cache.update.seconds:10800}")
    private int timeLimit;

    private final Logger log = LogManager.getLogger(this.getClass());

    private Boolean updating = false;

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

    public void dropCache() throws Exception {
        this.statsCache.dropCache();
    }

    public Map<String, Object> getStats() throws Exception {
        return this.statsCache.stats();
    }

    private void doUpdateCache() {
        log.info("Starting cache update");
        List<CacheEntry> entries = statsCache.getEntries();

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

    private void doPromoteCache() {
        log.info("Promoting shadow cache values to public");

        List<CacheEntry> entries = statsCache.getEntries();

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