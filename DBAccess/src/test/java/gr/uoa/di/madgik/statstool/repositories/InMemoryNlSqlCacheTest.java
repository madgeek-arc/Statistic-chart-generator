package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryNlSqlCacheTest {

    private static final String FP = "fingerprint-v1";

    private InMemoryNlSqlCache cache;

    @BeforeEach
    void setup() {
        cache = new InMemoryNlSqlCache();
    }

    private static NlCachedEntry entry(String sql) {
        return new NlCachedEntry(new QueryWithParameters(sql, List.of(), "p"), "");
    }

    private static NlCachedEntry entry(String sql, String description) {
        return new NlCachedEntry(new QueryWithParameters(sql, List.of(), "p"), description);
    }

    @Test
    void get_onEmptyCache_returnsNull() {
        assertNull(cache.get("openaire", "publications per year", FP));
    }

    @Test
    void put_thenGet_returnsSameValue() {
        NlCachedEntry e = new NlCachedEntry(
                new QueryWithParameters("SELECT COUNT(*) FROM result", List.of(), "openaire.public"), "");
        cache.put("openaire", "publications per year", e, FP);
        NlCachedEntry result = cache.get("openaire", "publications per year", FP);
        assertNotNull(result);
        assertEquals("SELECT COUNT(*) FROM result", result.qwp().getQuery());
    }

    @Test
    void put_thenGet_descriptionIsPreserved() {
        cache.put("openaire", "nl", entry("SELECT 1", "Counts publications per year"), FP);
        assertEquals("Counts publications per year", cache.get("openaire", "nl", FP).description());
    }

    @Test
    void get_withDifferentFingerprint_returnsNull() {
        cache.put("openaire", "nl", entry("SELECT 1"), "fingerprint-v1");
        assertNull(cache.get("openaire", "nl", "fingerprint-v2"));
    }

    @Test
    void get_withMatchingFingerprint_returnsValue() {
        cache.put("openaire", "nl", entry("SELECT 1"), "fingerprint-v1");
        assertNotNull(cache.get("openaire", "nl", "fingerprint-v1"));
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

    @Test
    void put_overwrites_previousValue() {
        cache.put("openaire", "nl", entry("SELECT 1 FROM result"), FP);
        cache.put("openaire", "nl", entry("SELECT 2 FROM result"), FP);
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "nl", FP).qwp().getQuery());
    }

    @Test
    void parametersArePreserved() {
        NlCachedEntry e = new NlCachedEntry(
                new QueryWithParameters("SELECT * FROM result WHERE year=?", List.of("2023"), "p"), "");
        cache.put("openaire", "results in 2023", e, FP);
        assertEquals(List.of("2023"), cache.get("openaire", "results in 2023", FP).qwp().getParameters());
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
    void drop_emptyCache_doesNotThrow() {
        assertDoesNotThrow(() -> cache.drop("openaire"));
        assertDoesNotThrow(() -> cache.drop(null));
    }
}
