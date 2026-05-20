package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DbNlSqlCacheTest {

    private static final String FP = "fingerprint-v1";

    private DbNlSqlCache cache;

    @BeforeEach
    void setup() throws Exception {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:nl_sql_cache_test_" + System.nanoTime() + ";hsqldb.tx=mvcc");
        ds.setUser("sa");
        ds.setPassword("");
        cache = new DbNlSqlCache(ds);
        cache.init();
    }

    private static NlCachedEntry entry(String sql) {
        return new NlCachedEntry(new QueryWithParameters(sql, List.of(), "openaire.public"), "");
    }

    private static NlCachedEntry entry(String sql, String description) {
        return new NlCachedEntry(new QueryWithParameters(sql, List.of(), "openaire.public"), description);
    }

    @Test
    void get_onEmptyCache_returnsNull() {
        assertNull(cache.get("openaire", "publications per year", FP));
    }

    @Test
    void put_thenGet_returnsSameValue() {
        cache.put("openaire", "publications per year", entry("SELECT COUNT(*) FROM result"), FP);

        NlCachedEntry result = cache.get("openaire", "publications per year", FP);
        assertNotNull(result);
        assertEquals("SELECT COUNT(*) FROM result", result.qwp().getQuery());
        assertEquals("openaire.public", result.qwp().getDbId());
    }

    @Test
    void put_thenGet_descriptionIsPreserved() {
        cache.put("openaire", "nl", entry("SELECT 1 FROM result", "Counts publications per year"), FP);
        assertEquals("Counts publications per year", cache.get("openaire", "nl", FP).description());
    }

    @Test
    void put_thenGet_parametersArePreserved() {
        NlCachedEntry e = new NlCachedEntry(
                new QueryWithParameters("SELECT * FROM result WHERE year=?", List.of("2023"), "openaire.public"), "");
        cache.put("openaire", "results in 2023", e, FP);

        List<Object> params = cache.get("openaire", "results in 2023", FP).qwp().getParameters();
        assertEquals(1, params.size());
        assertEquals("2023", params.get(0));
    }

    @Test
    void get_withDifferentFingerprint_returnsNull() {
        cache.put("openaire", "nl", entry("SELECT 1 FROM result"), "fingerprint-v1");
        assertNull(cache.get("openaire", "nl", "fingerprint-v2"));
    }

    @Test
    void get_withMatchingFingerprint_returnsValue() {
        cache.put("openaire", "nl", entry("SELECT 1 FROM result"), "fingerprint-v1");
        assertNotNull(cache.get("openaire", "nl", "fingerprint-v1"));
    }

    @Test
    void put_updatesFingerprint() {
        cache.put("openaire", "nl", entry("SELECT 1 FROM result"), "fingerprint-v1");
        cache.put("openaire", "nl", entry("SELECT 2 FROM result"), "fingerprint-v2");

        assertNull(cache.get("openaire", "nl", "fingerprint-v1"));
        assertNotNull(cache.get("openaire", "nl", "fingerprint-v2"));
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "nl", "fingerprint-v2").qwp().getQuery());
    }

    @Test
    void put_overwrites_previousValue() {
        cache.put("openaire", "nl", entry("SELECT 1 FROM result"), FP);
        cache.put("openaire", "nl", entry("SELECT 2 FROM result"), FP);
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "nl", FP).qwp().getQuery());
    }

    @Test
    void differentProfiles_doNotCollide() {
        cache.put("profile_a", "same nl", entry("SELECT 1 FROM result"), FP);
        cache.put("profile_b", "same nl", entry("SELECT 2 FROM result"), FP);

        assertEquals("SELECT 1 FROM result", cache.get("profile_a", "same nl", FP).qwp().getQuery());
        assertEquals("SELECT 2 FROM result", cache.get("profile_b", "same nl", FP).qwp().getQuery());
    }

    @Test
    void differentNlStrings_doNotCollide() {
        cache.put("openaire", "query one", entry("SELECT 1 FROM result"), FP);
        cache.put("openaire", "query two", entry("SELECT 2 FROM result"), FP);

        assertEquals("SELECT 1 FROM result", cache.get("openaire", "query one", FP).qwp().getQuery());
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "query two", FP).qwp().getQuery());
    }

    // --- evict ---

    @Test
    void evict_removesEntry() {
        cache.put("openaire", "nl", entry("SELECT 1"), FP);
        cache.evict("openaire", "nl");
        assertNull(cache.get("openaire", "nl", FP));
    }

    @Test
    void evict_doesNotAffectOtherEntries() {
        cache.put("openaire", "nl1", entry("SELECT 1"), FP);
        cache.put("openaire", "nl2", entry("SELECT 2"), FP);
        cache.evict("openaire", "nl1");
        assertNull(cache.get("openaire", "nl1", FP));
        assertNotNull(cache.get("openaire", "nl2", FP));
    }

    @Test
    void evict_nonExistentEntry_doesNotThrow() {
        assertDoesNotThrow(() -> cache.evict("openaire", "does not exist"));
    }

    // --- drop ---

    @Test
    void drop_withProfile_removesOnlyThatProfile() {
        cache.put("profile_a", "nl", entry("SELECT 1"), FP);
        cache.put("profile_b", "nl", entry("SELECT 2"), FP);
        cache.drop("profile_a");
        assertNull(cache.get("profile_a", "nl", FP));
        assertNotNull(cache.get("profile_b", "nl", FP));
    }

    @Test
    void drop_withNull_removesAll() {
        cache.put("profile_a", "nl1", entry("SELECT 1"), FP);
        cache.put("profile_b", "nl2", entry("SELECT 2"), FP);
        cache.drop(null);
        assertNull(cache.get("profile_a", "nl1", FP));
        assertNull(cache.get("profile_b", "nl2", FP));
    }

    @Test
    void drop_withBlankString_removesAll() {
        cache.put("openaire", "nl", entry("SELECT 1"), FP);
        cache.drop("  ");
        assertNull(cache.get("openaire", "nl", FP));
    }

    @Test
    void drop_emptyCache_doesNotThrow() {
        assertDoesNotThrow(() -> cache.drop("openaire"));
        assertDoesNotThrow(() -> cache.drop(null));
    }
}
