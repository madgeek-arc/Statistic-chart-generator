package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
public class StatsDBRepositoryTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    private HikariDataSource ds;
    private StatsDBRepository repo;

    @BeforeEach
    void setup() throws Exception {
        ds = new HikariDataSource();
        ds.setJdbcUrl(pg.getJdbcUrl());
        ds.setUsername(pg.getUsername());
        ds.setPassword(pg.getPassword());
        ds.setMaximumPoolSize(5);

        repo = new StatsDBRepository(ds);
        Field f = StatsDBRepository.class.getDeclaredField("enableCache");
        f.setAccessible(true);
        f.set(repo, true);
        repo.postInit();
        new JdbcTemplate(ds).execute("DELETE FROM cache_entry");
    }

    @AfterEach
    void teardown() {
        if (ds != null) ds.close();
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }

    private static String hexKey64(char ch) {
        char[] arr = new char[64];
        Arrays.fill(arr, ch);
        return new String(arr);
    }

    @Test
    public void storeAndRetrieveLargeQueryAndResult_noTruncation() throws Exception {
        String largeQuery = repeat("Q", 11000);
        List<Object> params = new ArrayList<>();
        params.add("p1");
        QueryWithParameters qwp = new QueryWithParameters(largeQuery, params, "pg");

        Result res = new Result();
        for (int i = 0; i < 5; i++) {
            res.addRow(Arrays.asList(i, "v" + i));
        }

        CacheEntry entry = new CacheEntry(hexKey64('a'), qwp, res);
        entry.setProfile("test-profile");
        entry.setExecTime(123);
        entry.setFresh(true);

        repo.storeEntry(entry);

        Result fetched = repo.get(entry.getKey());
        assertNotNull(fetched, "Entry should exist after storing and result must not be null");
        assertEquals(res.getRows().size(), fetched.getRows().size(), "Row count should round-trip");
    }

    @Test
    public void mergeUpsert_updatesStoredFields() throws Exception {
        String key = hexKey64('b');
        QueryWithParameters q1 = new QueryWithParameters("SELECT 1", List.of(), "pg");
        Result r1 = new Result();
        r1.addRow(List.of(1, "one"));
        CacheEntry e1 = new CacheEntry(key, q1, r1);
        e1.setProfile("prof");
        e1.setExecTime(10);
        repo.storeEntry(e1);

        QueryWithParameters q2 = new QueryWithParameters("SELECT 2", List.of("p"), "pg");
        Result r2 = new Result();
        r2.addRow(List.of(2, "two"));
        CacheEntry e2 = new CacheEntry(key, q2, r2);
        e2.setProfile("prof2");
        e2.setExecTime(20);
        e2.setFresh(true);
        repo.storeEntry(e2);

        Result fetched = repo.get(key);
        assertNotNull(fetched);
        assertEquals(1, fetched.getRows().size());
        assertEquals("two", fetched.getRows().get(0).get(1));
    }

    @Test
    public void storeEntry_doesNotOverwriteCounters() throws Exception {
        String key = hexKey64('f');
        QueryWithParameters q = new QueryWithParameters("SELECT cnt", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        CacheEntry e = new CacheEntry(key, q, r);
        e.setProfile("prof");
        e.setFresh(true);
        repo.storeEntry(e);

        repo.get(key);
        repo.get(key);

        e.setShadowResult(r);
        repo.storeEntry(e);

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "storeEntry must not overwrite total_hits");
        assertEquals(3, stored.getSessionHits(), "storeEntry must not overwrite session_hits");
    }

    @Test
    public void resetSessionHits_resetsSessionOnly_totalUnchanged() throws Exception {
        String key = hexKey64('g');
        QueryWithParameters q = new QueryWithParameters("SELECT reset", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        CacheEntry e = new CacheEntry(key, q, r);
        e.setProfile("prof");
        e.setFresh(true);
        repo.storeEntry(e);

        repo.get(key);
        repo.get(key);

        repo.resetSessionHits("prof");

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "resetSessionHits must not touch total_hits");
        assertEquals(0, stored.getSessionHits(), "resetSessionHits must set session_hits=0");
    }

    @Test
    public void shadowNullThenLarge_storesSuccessfully() throws Exception {
        String key = hexKey64('c');
        QueryWithParameters q = new QueryWithParameters("Q", List.of(1, 2, 3), "pg");
        Result r = new Result();
        r.addRow(List.of("x"));
        CacheEntry e = new CacheEntry(key, q, r);
        e.setProfile("prof");
        e.setFresh(true);
        repo.storeEntry(e);
        assertNotNull(repo.get(key));

        Result shadow = new Result();
        shadow.addRow(List.of(repeat("S", 11000)));
        e.setShadowResult(shadow);
        repo.storeEntry(e);

        assertNotNull(repo.get(key));
    }

    @Test
    public void freshFlag_defaultsFalse_setTrueAfterSave() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT fresh", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));

        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        List<CacheEntry> entries = repo.getEntries("prof");
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).isFresh(), "save() must set fresh=true");
    }

    @Test
    public void markAllStale_setsFreshFalse() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT stale", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        repo.save(q, r, 5, 0);

        assertTrue(repo.getEntries("prof").get(0).isFresh());

        repo.markAllStale("prof");

        assertFalse(repo.getEntries("prof").get(0).isFresh(), "markAllStale must set fresh=false");
    }

    @Test
    public void trickleRefreshEntry_setsFreshTrue() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT trickle", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        repo.markAllStale("prof");
        assertFalse(repo.getEntries("prof").get(0).isFresh());

        Result updated = new Result();
        updated.addRow(List.of(2));
        repo.trickleRefreshEntry(key, updated, 10, 0);

        assertTrue(repo.getEntries("prof").get(0).isFresh(), "trickleRefreshEntry must set fresh=true");
    }

    @Test
    public void getStaleEntries_orderedByTotalHitsDesc() throws Exception {
        for (int i = 0; i < 3; i++) {
            QueryWithParameters q = new QueryWithParameters("SELECT " + i, List.of(), "prof");
            Result r = new Result();
            r.addRow(List.of(i));
            repo.save(q, r, 5, 0);
            String key = StatsCache.getCacheKey(q);
            for (int j = 0; j <= i; j++) repo.get(key);
        }

        repo.markAllStale("prof");
        List<CacheEntry> stale = repo.getStaleEntries("prof");

        assertEquals(3, stale.size());
        assertTrue(stale.get(0).getTotalHits() >= stale.get(1).getTotalHits(),
                "getStaleEntries must order by total_hits DESC");
        assertTrue(stale.get(1).getTotalHits() >= stale.get(2).getTotalHits(),
                "getStaleEntries must order by total_hits DESC");
    }

    @Test
    public void save_preservesCountersOnExistingEntry() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT preserved", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        repo.get(key);
        repo.get(key);

        Result r2 = new Result();
        r2.addRow(List.of(99));
        repo.save(q, r2, 10, 0);

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "save() on existing entry must preserve total_hits");
        assertEquals(3, stored.getSessionHits(), "save() on existing entry must preserve session_hits");
    }

    @Test
    public void get_staleEntry_returnsCacheMiss() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT stale_serve", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(42));

        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        repo.markAllStale("prof");

        Result served = repo.get(key);
        assertNull(served, "Stale entry must return null (cache miss) — caller re-executes");
    }

    @Test
    public void get_staleEntry_incrementsCounters() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT stale_count", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));

        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        repo.markAllStale("prof");

        assertNull(repo.get(key));
        assertNull(repo.get(key));

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "Stale miss must still increment total_hits");
        assertEquals(3, stored.getSessionHits(), "Stale miss must still increment session_hits");
    }

    @Test
    public void hasShadowEntries_trueWhenShadowPresent() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT 1", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        CacheEntry e = new CacheEntry(StatsCache.getCacheKey(q), q, r);
        e.setProfile("prof");
        e.setFresh(true);
        e.setShadowResult(r);
        repo.storeEntry(e);

        assertTrue(repo.hasShadowEntries("prof"));
        assertFalse(repo.hasShadowEntries("other"));
    }

    @Test
    public void hasShadowEntries_falseWhenNoShadow() throws Exception {
        QueryWithParameters q = new QueryWithParameters("SELECT 1", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        repo.save(q, r, 5, 0);

        assertFalse(repo.hasShadowEntries("prof"), "save() never sets shadow");
    }

    @Test
    public void getProfilesWithShadows_returnsOnlyProfilesWithShadow() throws Exception {
        Result r = new Result();
        r.addRow(List.of(1));

        QueryWithParameters q1 = new QueryWithParameters("SELECT 1", List.of(), "openaire");
        CacheEntry e1 = new CacheEntry(StatsCache.getCacheKey(q1), q1, r);
        e1.setProfile("openaire");
        e1.setFresh(true);
        e1.setShadowResult(r);
        repo.storeEntry(e1);

        QueryWithParameters q2 = new QueryWithParameters("SELECT 2", List.of(), "egi");
        repo.save(q2, r, 5, 0);

        List<String> profiles = repo.getProfilesWithShadows();
        assertEquals(1, profiles.size());
        assertEquals("openaire", profiles.get(0));
    }
}
