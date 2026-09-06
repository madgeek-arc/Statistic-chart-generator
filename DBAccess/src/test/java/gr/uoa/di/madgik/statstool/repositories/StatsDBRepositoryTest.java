package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StatsDBRepositoryTest {

    private StatsDBRepository newRepo(String dbName) throws Exception {
        JDBCDataSource ds = new JDBCDataSource();
        // distinct in-memory DB per test
        ds.setUrl("jdbc:hsqldb:mem:" + dbName + ";hsqldb.tx=mvcc");
        ds.setUser("sa");
        ds.setPassword("");
        StatsDBRepository repo = new StatsDBRepository(ds);
        // Enable cache flag via reflection since @Value isn't processed in unit tests
        Field f = StatsDBRepository.class.getDeclaredField("enableCache");
        f.setAccessible(true);
        f.set(repo, true);
        // Initialize schema
        repo.postInit();
        return repo;
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
        StatsDBRepository repo = newRepo("cache_large_payload");

        // Build query string slightly above previous 10k cap (~11k chars)
        String largeQuery = repeat("Q", 11000);
        List<Object> params = new ArrayList<>();
        params.add("p1");
        QueryWithParameters qwp = new QueryWithParameters(largeQuery, params, "hsqldb");

        // Keep result payload modest; focus of this test is large query storage
        Result res = new Result();
        for (int i = 0; i < 5; i++) {
            res.addRow(Arrays.asList(i, "v" + i));
        }

        CacheEntry entry = new CacheEntry(hexKey64('a'), qwp, res);
        entry.setProfile("test-profile");
        entry.setExecTime(123);
        entry.setFresh(true);

        // Save to cache
        repo.storeEntry(entry);

        // Validate exists and get
        Result fetched = repo.get(entry.getKey());
        assertNotNull(fetched, "Entry should exist after storing and result must not be null");
        assertEquals(res.getRows().size(), fetched.getRows().size(), "Row count should round-trip");
    }

    @Test
    public void mergeUpsert_updatesStoredFields() throws Exception {
        StatsDBRepository repo = newRepo("cache_merge_upsert");

        String key = hexKey64('b');
        QueryWithParameters q1 = new QueryWithParameters("SELECT 1", List.of(), "hsqldb");
        Result r1 = new Result();
        r1.addRow(List.of(1, "one"));
        CacheEntry e1 = new CacheEntry(key, q1, r1);
        e1.setProfile("prof");
        e1.setExecTime(10);
        repo.storeEntry(e1);

        // Update some fields and store again (MERGE should update row)
        QueryWithParameters q2 = new QueryWithParameters("SELECT 2", List.of("p"), "hsqldb");
        Result r2 = new Result();
        r2.addRow(List.of(2, "two"));
        CacheEntry e2 = new CacheEntry(key, q2, r2);
        e2.setProfile("prof2");
        e2.setExecTime(20);
        e2.setFresh(true);
        repo.storeEntry(e2);

        // A get should increment total/session hits and return latest result (r2)
        Result fetched = repo.get(key);
        assertNotNull(fetched);
        assertEquals(1, fetched.getRows().size());
        assertEquals("two", fetched.getRows().get(0).get(1));
    }

    @Test
    public void postInit_migratesLegacyNarrowColumns() throws Exception {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:cache_legacy_schema;hsqldb.tx=mvcc");
        ds.setUser("sa");
        ds.setPassword("");

        // Recreate the pre-2026-02 schema: query/key VARCHAR(10000), profile VARCHAR(100),
        // and no queuetime column.
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("create table cache_entry (" +
                    "key varchar(10000) not null," +
                    "result longvarchar not null, " +
                    "shadow longvarchar, " +
                    "query varchar(10000) not null," +
                    "created timestamp default now() not null, " +
                    "updated timestamp default now() not null, " +
                    "total_hits int default 0 not null," +
                    "session_hits int default 0 not null," +
                    "pinned boolean default false not null," +
                    "exectime int default 0 not null," +
                    "profile varchar(100) not null)");
        }

        StatsDBRepository repo = new StatsDBRepository(ds);
        Field f = StatsDBRepository.class.getDeclaredField("enableCache");
        f.setAccessible(true);
        f.set(repo, true);
        repo.postInit();

        // A query whose JSON serialization far exceeds the legacy VARCHAR(10000) cap.
        String largeQuery = repeat("Q", 50000);
        QueryWithParameters qwp = new QueryWithParameters(largeQuery, new ArrayList<>(), "hsqldb");
        Result res = new Result();
        res.addRow(Arrays.asList(1, "v"));

        CacheEntry entry = new CacheEntry(hexKey64('d'), qwp, res);
        entry.setProfile("prof");
        entry.setFresh(true);

        repo.storeEntry(entry);

        assertNotNull(repo.get(entry.getKey()), "Entry should store after schema migration");
    }

    @Test
    public void storeEntry_doesNotOverwriteCounters() throws Exception {
        StatsDBRepository repo = newRepo("cache_counter_preserve");

        String key = hexKey64('f');
        QueryWithParameters q = new QueryWithParameters("SELECT cnt", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        CacheEntry e = new CacheEntry(key, q, r);
        e.setProfile("prof");
        e.setFresh(true);
        repo.storeEntry(e); // INSERT: total=1, session=1

        repo.get(key); // total=2, session=2
        repo.get(key); // total=3, session=3

        // Simulate update cycle: storeEntry must NOT reset counters
        e.setShadowResult(r);
        repo.storeEntry(e);

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "storeEntry must not overwrite total_hits");
        assertEquals(3, stored.getSessionHits(), "storeEntry must not overwrite session_hits");
    }

    @Test
    public void resetSessionHits_resetsSessionOnly_totalUnchanged() throws Exception {
        StatsDBRepository repo = newRepo("cache_reset_session");

        String key = hexKey64('g');
        QueryWithParameters q = new QueryWithParameters("SELECT reset", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        CacheEntry e = new CacheEntry(key, q, r);
        e.setProfile("prof");
        e.setFresh(true);
        repo.storeEntry(e); // total=1, session=1

        repo.get(key); // total=2, session=2
        repo.get(key); // total=3, session=3

        repo.resetSessionHits("prof");

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "resetSessionHits must not touch total_hits");
        assertEquals(0, stored.getSessionHits(), "resetSessionHits must set session_hits=0");
    }

    @Test
    public void shadowNullThenLarge_storesSuccessfully() throws Exception {
        StatsDBRepository repo = newRepo("cache_shadow");

        String key = hexKey64('c');
        QueryWithParameters q = new QueryWithParameters("Q", List.of(1, 2, 3), "hsqldb");
        Result r = new Result();
        r.addRow(List.of("x"));
        CacheEntry e = new CacheEntry(key, q, r);
        e.setProfile("prof");
        e.setFresh(true);
        // no shadow initially
        repo.storeEntry(e);
        assertNotNull(repo.get(key));

        // Now add a very large shadow and store again
        Result shadow = new Result();
        shadow.addRow(List.of(repeat("S", 11000)));
        e.setShadowResult(shadow);
        repo.storeEntry(e);

        // Ensure get still works after updating shadow
        Result fetched = repo.get(key);
        assertNotNull(fetched);
    }

    @Test
    public void freshFlag_defaultsFalse_setTrueAfterSave() throws Exception {
        StatsDBRepository repo = newRepo("cache_fresh_flag");

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
        StatsDBRepository repo = newRepo("cache_mark_stale");

        QueryWithParameters q = new QueryWithParameters("SELECT stale", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        // Confirm fresh=true after save
        assertTrue(repo.getEntries("prof").get(0).isFresh());

        repo.markAllStale("prof");

        assertFalse(repo.getEntries("prof").get(0).isFresh(), "markAllStale must set fresh=false");
    }

    @Test
    public void trickleRefreshEntry_setsFreshTrue() throws Exception {
        StatsDBRepository repo = newRepo("cache_trickle_refresh");

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
        StatsDBRepository repo = newRepo("cache_stale_order");

        // Create 3 entries with different total_hits via get() calls
        for (int i = 0; i < 3; i++) {
            QueryWithParameters q = new QueryWithParameters("SELECT " + i, List.of(), "prof");
            Result r = new Result();
            r.addRow(List.of(i));
            repo.save(q, r, 5, 0);
            String key = StatsCache.getCacheKey(q);
            // call get() i times to build up total_hits: entry 0→1 hit, entry 1→2 hits, entry 2→3 hits
            for (int j = 0; j <= i; j++) repo.get(key);
        }

        repo.markAllStale("prof");
        List<CacheEntry> stale = repo.getStaleEntries("prof");

        assertEquals(3, stale.size());
        // Entries should be ordered by total_hits descending: 4, 3, 2
        assertTrue(stale.get(0).getTotalHits() >= stale.get(1).getTotalHits(),
                "getStaleEntries must order by total_hits DESC");
        assertTrue(stale.get(1).getTotalHits() >= stale.get(2).getTotalHits(),
                "getStaleEntries must order by total_hits DESC");
    }

    @Test
    public void save_preservesCountersOnExistingEntry() throws Exception {
        StatsDBRepository repo = newRepo("cache_save_counters");

        QueryWithParameters q = new QueryWithParameters("SELECT preserved", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(1));
        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        // Build up counters
        repo.get(key); // total=2
        repo.get(key); // total=3

        // save() again (simulates user cold re-execution with updated result)
        Result r2 = new Result();
        r2.addRow(List.of(99));
        repo.save(q, r2, 10, 0);

        CacheEntry stored = repo.getEntries("prof").get(0);
        assertEquals(3, stored.getTotalHits(), "save() on existing entry must preserve total_hits");
        assertEquals(3, stored.getSessionHits(), "save() on existing entry must preserve session_hits");
    }

    @Test
    public void get_staleEntry_returnsCacheMiss() throws Exception {
        StatsDBRepository repo = newRepo("cache_stale_miss");

        QueryWithParameters q = new QueryWithParameters("SELECT stale_serve", List.of(), "prof");
        Result r = new Result();
        r.addRow(List.of(42));

        repo.save(q, r, 5, 0);
        String key = StatsCache.getCacheKey(q);

        // Stale entries must not be served — caller must re-execute against main DB.
        repo.markAllStale("prof");

        Result served = repo.get(key);
        assertNull(served, "Stale entry must return null (cache miss) — caller re-executes");
    }
}
