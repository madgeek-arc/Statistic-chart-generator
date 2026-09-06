package gr.uoa.di.madgik.statstool.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.TimedResult;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.repositories.QueryPriority;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

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

    // Tracks profiles currently being updated. The sentinel "*" represents a
    // null (all-profiles) update. Access must be guarded by synchronized(activeUpdates).
    private final Set<String> activeUpdates = ConcurrentHashMap.newKeySet();
    private static final String ALL_PROFILES = "*";

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean trickleStopRequested = new AtomicBoolean(false);
    private final Set<String> activeTrickles = ConcurrentHashMap.newKeySet();

    @Override
    public void updateCache(String profile, Integer limit, Integer maxSeconds) {
        int effectiveLimit = (limit != null) ? limit : numberLimit;
        int effectiveSeconds = (maxSeconds != null) ? maxSeconds : timeLimit;
        String key = (profile == null) ? ALL_PROFILES : profile;

        synchronized (activeUpdates) {
            // A global update blocks everything; a profile update blocks the same profile
            // and is blocked by a global update.
            if (activeUpdates.contains(ALL_PROFILES) || activeUpdates.contains(key)) {
                log.info("Cache update for '{}' already running; ignoring request", key);
                return;
            }
            // Starting a global update is only allowed when no other update is running.
            if (key.equals(ALL_PROFILES) && !activeUpdates.isEmpty()) {
                log.info("Profile-specific updates are running; ignoring global update request");
                return;
            }
            activeUpdates.add(key);
        }

        log.info("Updating cache for {} [limit={}, maxSeconds={}]",
                profile != null ? "'" + profile + "'" : "all profiles", effectiveLimit, effectiveSeconds);

        new Thread(() -> {
            try {
                stopRequested.set(false);
                doUpdateCache(profile, effectiveLimit, effectiveSeconds);
            } finally {
                activeUpdates.remove(key);
            }
        }).start();
    }

    @Override
    public void stopUpdate() {
        if (!activeUpdates.isEmpty()) {
            log.info("Stop requested for running cache update(s): {}", activeUpdates);
            stopRequested.set(true);
        } else {
            log.info("No cache update running; stopUpdate is a no-op");
        }
    }

    @Override
    public void trickleUpdate(String profile) {
        String key = (profile == null) ? ALL_PROFILES : profile;

        synchronized (activeTrickles) {
            if (activeTrickles.contains(ALL_PROFILES) || activeTrickles.contains(key)) {
                log.info("Trickle update for '{}' already running; ignoring", key);
                return;
            }
            if (key.equals(ALL_PROFILES) && !activeTrickles.isEmpty()) {
                log.info("Profile-specific trickles running; ignoring global trickle request");
                return;
            }
            activeTrickles.add(key);
        }

        log.info("Starting trickle update for {}", profile != null ? "'" + profile + "'" : "all profiles");

        new Thread(() -> {
            try {
                doTrickleUpdate(profile);
            } finally {
                activeTrickles.remove(key);
            }
        }).start();
    }

    @Override
    public void promoteCache(String profile) {
        log.info("Promoting cache for " + (profile != null ? "'" + profile + "'" : "all") + " profile(s)");
        this.doPromoteCache(profile);
    }

    public void dropCache(String profile) throws Exception {
        log.info("Dropping cache for " + (profile != null ? "'" + profile + "'" : "all") + " profile(s)");
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

        // Signal any running trickle to stop.
        trickleStopRequested.set(true);

        List<CacheEntry> entries = statsCache.getEntries(profile);
        entries.sort(new EntriesComparator());

        long startTime = new Date().getTime();

        IntStream.range(0, entries.size()).parallel().forEach(slot -> {
            CacheEntry entry = entries.get(slot);
            try {
                if (!stopRequested.get() && slot < effectiveLimit && new Date().getTime() < startTime + effectiveSeconds * 1000L) {
                    log.debug("{}: Updating entry {} ({})", slot, entry.getKey(), entry.getQuery().getDbId());

                    TimedResult timedResult = statsRepository.executeQuery(
                            entry.getQuery().getQuery(),
                            entry.getQuery().getParameters(),
                            entry.getQuery().getDbId().replace("public", "shadow"),
                            QueryPriority.CACHE_UPDATE);

                    entry.setShadowResult(timedResult.result);
                    entry.setFresh(true);
                    entry.setExecTime(timedResult.execTimeMs);
                    entry.setQueueTime(timedResult.queueTimeMs);
                } else {
                    log.info("Skipping entry {} (limit/time/stop). Shadow cleared.", entry.getKey());
                    entry.setShadowResult(null);
                    // fresh unchanged — entry still serves users from cache until promote
                }
                statsCache.storeEntry(entry);
            } catch (JsonProcessingException e) {
                log.error("Error storing cache entry", e);
            } catch (Exception e) {
                log.error("Error updating entry {}", entry, e);
                statsCache.deleteEntry(entry.getKey());
            }
        });

        log.info("Finished cache update!");
    }

    private void doPromoteCache(String profile) {
        log.info("Promoting shadow cache values to result");

        if (!statsCache.hasShadowEntries(profile)) {
            log.warn("promoteCache: no shadow entries found for {} — was updateCache run first? Aborting.",
                    profile != null ? "'" + profile + "'" : "all profiles");
            return;
        }

        // Mark all entries stale before loading. Entries with a shadow get promoted to
        // fresh=true below. Entries without a shadow (skipped during update) remain
        // stale and become trickle targets. Order matters: markAllStale then getEntries
        // so in-memory entries start with fresh=false.
        statsCache.markAllStale(profile);

        List<CacheEntry> entries = statsCache.getEntries(profile);

        entries.forEach(entry -> {
            try {
                if (entry.getShadowResult() != null) {
                    entry.setResult(entry.getShadowResult());
                    entry.setShadowResult(null);
                    entry.setFresh(true);
                }
                // fresh=false entries: result unchanged (stale, trickle target)
                statsCache.storeEntry(entry);
            } catch (Exception e) {
                log.error("Error promoting cache entry {}", entry.getKey(), e);
            }
        });

        statsCache.resetSessionHits(profile);

        // Auto-start trickle to refresh entries that were not shadow-updated this cycle.
        trickleUpdate(profile);
    }

    private void doTrickleUpdate(String profile) {
        trickleStopRequested.set(false); // clear stop flag at trickle start

        List<CacheEntry> staleEntries = statsCache.getStaleEntries(profile);
        log.info("Trickle: {} stale entries to refresh for {}", staleEntries.size(),
                profile != null ? "'" + profile + "'" : "all profiles");

        for (CacheEntry entry : staleEntries) {
            if (trickleStopRequested.get()) {
                log.info("Trickle update interrupted by new cache update cycle");
                break;
            }
            try {
                TimedResult timedResult = statsRepository.executeQuery(
                        entry.getQuery().getQuery(),
                        entry.getQuery().getParameters(),
                        entry.getQuery().getDbId(),
                        QueryPriority.TRICKLE);
                statsCache.trickleRefreshEntry(entry.getKey(), timedResult.result,
                        timedResult.execTimeMs, timedResult.queueTimeMs);
            } catch (Exception e) {
                log.error("Trickle error for entry {}", entry.getKey(), e);
            }
        }

        log.info("Trickle update finished");
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
            return o1.getSessionHits() > o2.getSessionHits() ? -1 : 1;

        if (o1.getTotalHits() != o2.getTotalHits())
            return o1.getTotalHits() > o2.getTotalHits() ? -1 : 1;

        return 0;
    }
}
