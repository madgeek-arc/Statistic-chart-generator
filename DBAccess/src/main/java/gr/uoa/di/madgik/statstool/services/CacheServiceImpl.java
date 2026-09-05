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
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final AtomicBoolean updating = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    @Override
    public void updateCache(String profile, Integer limit, Integer maxSeconds) {
        int effectiveLimit = (limit != null) ? limit : numberLimit;
        int effectiveSeconds = (maxSeconds != null) ? maxSeconds : timeLimit;

        log.info("Updating cache for " + (profile != null ? "'" + profile + "'" : "all") +
                " profile(s) [limit=" + effectiveLimit + ", maxSeconds=" + effectiveSeconds + "]");

        if (updating.compareAndSet(false, true)) {
            stopRequested.set(false);
            new Thread(() -> {
                try {
                    doUpdateCache(profile, effectiveLimit, effectiveSeconds);
                } finally {
                    updating.set(false);
                }
            }).start();
        } else {
            log.info("Cache update already running; ignoring request");
        }
    }

    @Override
    public void stopUpdate() {
        if (updating.get()) {
            log.info("Stop requested for running cache update");
            stopRequested.set(true);
        } else {
            log.info("No cache update running; stopUpdate is a no-op");
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

    private void doUpdateCache(String profile, int effectiveLimit, int effectiveSeconds) {
        log.info("Starting cache update");
        List<CacheEntry> entries = statsCache.getEntries(profile);

        entries.sort(new EntriesComparator());

        long startTime = new Date().getTime();

        // IntStream.range preserves sorted order: entry at index N gets slot N, so
        // pinned/high-hit entries are guaranteed to fall within effectiveLimit.
        java.util.stream.IntStream.range(0, entries.size()).parallel().forEach(slot -> {
            CacheEntry entry = entries.get(slot);
            try {

                if (!stopRequested.get() && slot < effectiveLimit && new Date().getTime() < startTime + effectiveSeconds * 1000L) {
                    log.debug(slot + ". Updating entry " + entry.getKey() + "(" + entry.getQuery().getDbId() + ") with query " + entry.getQuery());

                    TimedResult timedResult = statsRepository.executeQuery(entry.getQuery().getQuery(), entry.getQuery().getParameters(), entry.getQuery().getDbId().replace("public", "shadow"));

                    entry.setShadowResult(timedResult.result);
                    entry.setExecTime(timedResult.execTimeMs);
                    entry.setQueueTime(timedResult.queueTimeMs);
                } else {
                    log.info("Skipping entry " + entry.getKey() + " (limit/time exceeded or stop requested). Invalidating shadow.");

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
            try {
                if (entry.getShadowResult() != null) {
                    entry.setResult(entry.getShadowResult());
                    entry.setValid(true);
                } else {
                    // No shadow populated (limit exceeded or query failed during update).
                    // Mark invalid so exists() returns false; counters are preserved.
                    entry.setValid(false);
                }
                entry.setShadowResult(null);
                entry.setUpdated(new Date());
                statsCache.storeEntry(entry);
            } catch (Exception e) {
                log.error("Error promoting cache entry " + entry.getKey(), e);
            }
        });

        // Reset session_hits for the promoted profile in a single statement.
        // Counters are never touched by storeEntry; this is the only place session_hits resets.
        statsCache.resetSessionHits(profile);
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
