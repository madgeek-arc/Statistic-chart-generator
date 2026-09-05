package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.TimedResult;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.repositories.StatsCache;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CacheServiceImplTest {

    @Mock private StatsRepository statsRepository;
    @Mock private StatsCache statsCache;
    @Mock private NlSqlCache nlSqlCache;
    @Mock private NlOptionsCache nlOptionsCache;

    @InjectMocks private CacheServiceImpl service;

    @BeforeEach
    void setProperties() throws Exception {
        setField("numberLimit", 5000);
        setField("timeLimit", 10800);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setField(String name, Object value) throws Exception {
        Field f = CacheServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @SuppressWarnings("unchecked")
    private void waitForUpdate() throws Exception {
        Field f = CacheServiceImpl.class.getDeclaredField("activeUpdates");
        f.setAccessible(true);
        Set<String> activeUpdates = (Set<String>) f.get(service);
        long deadline = System.currentTimeMillis() + 5000;
        while (!activeUpdates.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(activeUpdates.isEmpty(), "Update did not finish within 5 s");
    }

    private static List<CacheEntry> makeEntries(int n) {
        List<CacheEntry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            QueryWithParameters q = new QueryWithParameters("SELECT " + i, List.of(), "public");
            Result r = new Result();
            r.addRow(List.of(i));
            CacheEntry e = new CacheEntry("key" + i, q, r);
            e.setProfile("public");
            list.add(e);
        }
        return list;
    }

    private static TimedResult shadowResult() {
        Result r = new Result();
        r.addRow(List.of("shadow"));
        return new TimedResult(r, 10, 0);
    }

    // -------------------------------------------------------------------------
    // Concurrent call semantics
    // -------------------------------------------------------------------------

    @Test
    public void sameProfile_secondCallIsNoOp() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(statsCache.getEntries(eq("openaire"))).thenAnswer(inv -> {
            started.countDown();
            release.await();
            return new ArrayList<>();
        });

        service.updateCache("openaire", 100, 60);
        assertTrue(started.await(2, TimeUnit.SECONDS), "First update must start");

        // Same profile while first is running: must be a no-op, must not throw
        assertDoesNotThrow(() -> service.updateCache("openaire", 100, 60));

        release.countDown();
        waitForUpdate();

        // getEntries called exactly once — second call ignored
        verify(statsCache, times(1)).getEntries(any());
    }

    @Test
    public void differentProfiles_bothRunConcurrently() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        when(statsCache.getEntries(any())).thenAnswer(inv -> {
            bothStarted.countDown();
            release.await();
            return new ArrayList<>();
        });

        service.updateCache("openaire", 100, 60);
        service.updateCache("egi", 100, 60);

        // Both threads must start — they are different profiles
        assertTrue(bothStarted.await(2, TimeUnit.SECONDS), "Both profile updates must run concurrently");

        release.countDown();
        waitForUpdate();

        verify(statsCache, times(2)).getEntries(any());
    }

    @Test
    public void globalUpdate_blocksProfileUpdate() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(statsCache.getEntries(isNull())).thenAnswer(inv -> {
            started.countDown();
            release.await();
            return new ArrayList<>();
        });

        service.updateCache(null, 100, 60);   // global
        assertTrue(started.await(2, TimeUnit.SECONDS));

        // Profile update while global runs: must be no-op
        assertDoesNotThrow(() -> service.updateCache("openaire", 100, 60));

        release.countDown();
        waitForUpdate();

        verify(statsCache, times(1)).getEntries(any());
    }

    @Test
    public void profileUpdate_blocksGlobalUpdate() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(statsCache.getEntries(eq("openaire"))).thenAnswer(inv -> {
            started.countDown();
            release.await();
            return new ArrayList<>();
        });

        service.updateCache("openaire", 100, 60);
        assertTrue(started.await(2, TimeUnit.SECONDS));

        // Global update while a profile update runs: must be no-op
        assertDoesNotThrow(() -> service.updateCache(null, 100, 60));

        release.countDown();
        waitForUpdate();

        verify(statsCache, times(1)).getEntries(any());
    }

    // -------------------------------------------------------------------------
    // limit / maxSeconds parameter override
    // -------------------------------------------------------------------------

    @Test
    public void updateCache_respectsExplicitLimit() throws Exception {
        when(statsCache.getEntries(any())).thenReturn(makeEntries(5));
        when(statsRepository.executeQuery(any(), any(), any())).thenReturn(shadowResult());

        service.updateCache(null, 2, 3600);
        waitForUpdate();

        // At most 2 shadow queries (limit=2); all 5 storeEntry calls happen
        verify(statsRepository, atMost(2)).executeQuery(any(), any(), any());
        verify(statsCache, times(5)).storeEntry(any());
    }

    @Test
    public void updateCache_nullParams_fallBackToProperties() throws Exception {
        setField("numberLimit", 2);
        when(statsCache.getEntries(any())).thenReturn(makeEntries(5));
        when(statsRepository.executeQuery(any(), any(), any())).thenReturn(shadowResult());

        service.updateCache(null, null, null);
        waitForUpdate();

        verify(statsRepository, atMost(2)).executeQuery(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // stopUpdate
    // -------------------------------------------------------------------------

    @Test
    public void stopUpdate_setsFlag_whenUpdateRunning() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(statsCache.getEntries(any())).thenAnswer(inv -> {
            started.countDown();
            release.await();
            return new ArrayList<>();
        });

        service.updateCache(null, 10, 3600);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        service.stopUpdate();

        Field f = CacheServiceImpl.class.getDeclaredField("stopRequested");
        f.setAccessible(true);
        assertTrue(((AtomicBoolean) f.get(service)).get(), "stopRequested must be true after stopUpdate()");

        release.countDown();
        waitForUpdate();
    }

    @Test
    public void stopUpdate_reducesQueriesExecuted() throws Exception {
        int total = 20;
        AtomicInteger queryCount = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);

        when(statsCache.getEntries(any())).thenReturn(makeEntries(total));
        when(statsRepository.executeQuery(any(), any(), any())).thenAnswer(inv -> {
            firstStarted.countDown();
            Thread.sleep(50); // slow enough to allow stop to be observed
            queryCount.incrementAndGet();
            return shadowResult();
        });

        service.updateCache(null, total, 3600);
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        service.stopUpdate();
        waitForUpdate();

        assertTrue(queryCount.get() < total,
                "stopUpdate must cause fewer than " + total + " queries; got " + queryCount.get());
    }

    @Test
    public void stopUpdate_whenNotRunning_isNoOp() {
        assertDoesNotThrow(() -> service.stopUpdate());
    }

    @Test
    public void afterStop_nextUpdateStartsClean() throws Exception {
        // Run and stop first update
        CountDownLatch r1 = new CountDownLatch(1);
        when(statsCache.getEntries(any())).thenAnswer(inv -> { r1.countDown(); return new ArrayList<>(); });
        service.updateCache(null, 10, 3600);
        assertTrue(r1.await(2, TimeUnit.SECONDS));
        service.stopUpdate();
        waitForUpdate();

        // Second update must start and stopRequested must be cleared
        CountDownLatch r2 = new CountDownLatch(1);
        when(statsCache.getEntries(any())).thenAnswer(inv -> { r2.countDown(); return new ArrayList<>(); });
        service.updateCache(null, 10, 3600);
        assertTrue(r2.await(2, TimeUnit.SECONDS), "Second update must start after stop");

        Field f = CacheServiceImpl.class.getDeclaredField("stopRequested");
        f.setAccessible(true);
        assertFalse(((AtomicBoolean) f.get(service)).get(),
                "stopRequested must be cleared when new update starts");

        waitForUpdate();
    }
}
