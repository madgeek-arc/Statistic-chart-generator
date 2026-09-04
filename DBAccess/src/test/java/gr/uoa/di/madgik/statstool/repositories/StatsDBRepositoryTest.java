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

        // Save to cache
        repo.storeEntry(entry);

        // Validate exists and get
        assertTrue(repo.exists(entry.getKey()), "Entry should exist after storing");
        Result fetched = repo.get(entry.getKey());
        assertNotNull(fetched, "Fetched result must not be null");
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

        repo.storeEntry(entry);

        assertTrue(repo.exists(entry.getKey()), "Entry should store after schema migration");
        assertNotNull(repo.get(entry.getKey()));
    }

    @Test
    public void postInit_trimsCharPaddedKeys_existingEntriesStillReachable() throws Exception {
        // Simulates a persisted volume that was written with the old CHAR(64) key column.
        // CHAR(64) stores 32-char MD5 keys padded with 32 trailing spaces.
        // After ALTER COLUMN key varchar(64), the padding is preserved in storage —
        // a lookup with a clean 32-char key would miss unless we also TRIM.
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:cache_char_key_migration;hsqldb.tx=mvcc");
        ds.setUser("sa");
        ds.setPassword("");

        String cleanKey = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"; // 32-char MD5-like key
        String paddedKey = cleanKey + repeat(" ", 32);           // as CHAR(64) would store it

        // Create old schema with CHAR(64) key column and insert a padded key directly
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("create table cache_entry (" +
                    "key char(64) not null," +
                    "result longvarchar not null, " +
                    "shadow longvarchar, " +
                    "query longvarchar not null," +
                    "created timestamp default now() not null, " +
                    "updated timestamp default now() not null, " +
                    "total_hits int default 0 not null," +
                    "session_hits int default 0 not null," +
                    "pinned boolean default false not null," +
                    "exectime int default 0 not null," +
                    "queuetime int default 0 not null," +
                    "profile varchar(255) not null)");
            // Insert a row directly with the padded key (as the old CHAR column would produce)
            st.execute("insert into cache_entry(key,result,query,profile) values('" +
                    paddedKey + "','{\"rows\":[]}','{\"query\":\"Q\",\"parameters\":[],\"dbId\":\"h\"}','p')");
        }

        // Run postInit — this should ALTER key to varchar(64) then TRIM existing rows
        StatsDBRepository repo = new StatsDBRepository(ds);
        Field f = StatsDBRepository.class.getDeclaredField("enableCache");
        f.setAccessible(true);
        f.set(repo, true);
        repo.postInit();

        // Lookup with clean (non-padded) key must find the migrated entry
        assertTrue(repo.exists(cleanKey),
                "Entry stored with padded CHAR key must be reachable after TRIM migration via clean key");
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
        // no shadow initially
        repo.storeEntry(e);
        assertTrue(repo.exists(key));

        // Now add a very large shadow and store again
        Result shadow = new Result();
        shadow.addRow(List.of(repeat("S", 11000)));
        e.setShadowResult(shadow);
        repo.storeEntry(e);

        // Ensure get still works after updating shadow
        Result fetched = repo.get(key);
        assertNotNull(fetched);
    }
}
