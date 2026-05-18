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

    @Test
    void get_onEmptyCache_returnsNull() {
        assertNull(cache.get("openaire", "publications per year", FP));
    }

    @Test
    void put_thenGet_returnsSameValue() {
        QueryWithParameters qwp = new QueryWithParameters("SELECT COUNT(*) FROM result", List.of(), "openaire.public");
        cache.put("openaire", "publications per year", qwp, FP);

        QueryWithParameters result = cache.get("openaire", "publications per year", FP);
        assertNotNull(result);
        assertEquals("SELECT COUNT(*) FROM result", result.getQuery());
        assertEquals("openaire.public", result.getDbId());
    }

    @Test
    void put_thenGet_parametersArePreserved() {
        QueryWithParameters qwp = new QueryWithParameters(
                "SELECT * FROM result WHERE year=?", List.of("2023"), "openaire.public");
        cache.put("openaire", "results in 2023", qwp, FP);

        List<Object> params = cache.get("openaire", "results in 2023", FP).getParameters();
        assertEquals(1, params.size());
        assertEquals("2023", params.get(0));
    }

    @Test
    void get_withDifferentFingerprint_returnsNull() {
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 1 FROM result", List.of(), "p"), "fingerprint-v1");
        assertNull(cache.get("openaire", "nl", "fingerprint-v2"));
    }

    @Test
    void get_withMatchingFingerprint_returnsValue() {
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 1 FROM result", List.of(), "p"), "fingerprint-v1");
        assertNotNull(cache.get("openaire", "nl", "fingerprint-v1"));
    }

    @Test
    void put_updatesFingerprint() {
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 1 FROM result", List.of(), "p"), "fingerprint-v1");
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 2 FROM result", List.of(), "p"), "fingerprint-v2");

        assertNull(cache.get("openaire", "nl", "fingerprint-v1"));
        assertNotNull(cache.get("openaire", "nl", "fingerprint-v2"));
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "nl", "fingerprint-v2").getQuery());
    }

    @Test
    void put_overwrites_previousValue() {
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 1 FROM result", List.of(), "p"), FP);
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 2 FROM result", List.of(), "p"), FP);
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "nl", FP).getQuery());
    }

    @Test
    void differentProfiles_doNotCollide() {
        cache.put("profile_a", "same nl", new QueryWithParameters("SELECT 1 FROM result", List.of(), "a"), FP);
        cache.put("profile_b", "same nl", new QueryWithParameters("SELECT 2 FROM result", List.of(), "b"), FP);

        assertEquals("SELECT 1 FROM result", cache.get("profile_a", "same nl", FP).getQuery());
        assertEquals("SELECT 2 FROM result", cache.get("profile_b", "same nl", FP).getQuery());
    }

    @Test
    void differentNlStrings_doNotCollide() {
        cache.put("openaire", "query one", new QueryWithParameters("SELECT 1 FROM result", List.of(), "p"), FP);
        cache.put("openaire", "query two", new QueryWithParameters("SELECT 2 FROM result", List.of(), "p"), FP);

        assertEquals("SELECT 1 FROM result", cache.get("openaire", "query one", FP).getQuery());
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "query two", FP).getQuery());
    }

    // --- evict ---

    @Test
    void evict_removesEntry() {
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 1", List.of(), "p"), FP);
        cache.evict("openaire", "nl");
        assertNull(cache.get("openaire", "nl", FP));
    }

    @Test
    void evict_doesNotAffectOtherEntries() {
        cache.put("openaire", "nl1", new QueryWithParameters("SELECT 1", List.of(), "p"), FP);
        cache.put("openaire", "nl2", new QueryWithParameters("SELECT 2", List.of(), "p"), FP);
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
        cache.put("profile_a", "nl", new QueryWithParameters("SELECT 1", List.of(), "p"), FP);
        cache.put("profile_b", "nl", new QueryWithParameters("SELECT 2", List.of(), "p"), FP);
        cache.drop("profile_a");
        assertNull(cache.get("profile_a", "nl", FP));
        assertNotNull(cache.get("profile_b", "nl", FP));
    }

    @Test
    void drop_withNull_removesAll() {
        cache.put("profile_a", "nl1", new QueryWithParameters("SELECT 1", List.of(), "p"), FP);
        cache.put("profile_b", "nl2", new QueryWithParameters("SELECT 2", List.of(), "p"), FP);
        cache.drop(null);
        assertNull(cache.get("profile_a", "nl1", FP));
        assertNull(cache.get("profile_b", "nl2", FP));
    }

    @Test
    void drop_withBlankString_removesAll() {
        cache.put("openaire", "nl", new QueryWithParameters("SELECT 1", List.of(), "p"), FP);
        cache.drop("  ");
        assertNull(cache.get("openaire", "nl", FP));
    }

    @Test
    void drop_emptyCache_doesNotThrow() {
        assertDoesNotThrow(() -> cache.drop("openaire"));
        assertDoesNotThrow(() -> cache.drop(null));
    }
}
