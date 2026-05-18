package gr.uoa.di.madgik.statstool.repositories;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DbNlOptionsCacheTest {

    private static final String VER = "v1";

    private DbNlOptionsCache cache;

    @BeforeEach
    void setup() throws Exception {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:nl_options_cache_test_" + System.nanoTime() + ";hsqldb.tx=mvcc");
        ds.setUser("sa");
        ds.setPassword("");
        cache = new DbNlOptionsCache(ds);
        cache.init();
    }

    @Test
    void get_onEmptyCache_returnsNull() {
        assertNull(cache.get("HighCharts", "blue bar chart", VER));
    }

    @Test
    void put_thenGet_returnsSameValue() {
        cache.put("HighCharts", "blue bar chart", "{\"colors\":[\"blue\"]}", VER);
        assertEquals("{\"colors\":[\"blue\"]}", cache.get("HighCharts", "blue bar chart", VER));
    }

    @Test
    void get_withDifferentPromptVersion_returnsNull() {
        cache.put("HighCharts", "blue bar chart", "{}", "v1");
        assertNull(cache.get("HighCharts", "blue bar chart", "v2"));
    }

    @Test
    void get_withMatchingPromptVersion_returnsValue() {
        cache.put("HighCharts", "blue bar chart", "{}", "v1");
        assertNotNull(cache.get("HighCharts", "blue bar chart", "v1"));
    }

    @Test
    void put_updatesPromptVersion() {
        cache.put("HighCharts", "desc", "{\"a\":1}", "v1");
        cache.put("HighCharts", "desc", "{\"a\":2}", "v2");

        assertNull(cache.get("HighCharts", "desc", "v1"));
        assertEquals("{\"a\":2}", cache.get("HighCharts", "desc", "v2"));
    }

    @Test
    void put_overwrites_previousValue() {
        cache.put("HighCharts", "desc", "{\"a\":1}", VER);
        cache.put("HighCharts", "desc", "{\"a\":2}", VER);
        assertEquals("{\"a\":2}", cache.get("HighCharts", "desc", VER));
    }

    @Test
    void differentLibraries_doNotCollide() {
        cache.put("HighCharts", "same desc", "{\"a\":1}", VER);
        cache.put("eCharts", "same desc", "{\"b\":2}", VER);

        assertEquals("{\"a\":1}", cache.get("HighCharts", "same desc", VER));
        assertEquals("{\"b\":2}", cache.get("eCharts", "same desc", VER));
    }

    @Test
    void differentDescriptions_doNotCollide() {
        cache.put("HighCharts", "desc one", "{\"a\":1}", VER);
        cache.put("HighCharts", "desc two", "{\"b\":2}", VER);

        assertEquals("{\"a\":1}", cache.get("HighCharts", "desc one", VER));
        assertEquals("{\"b\":2}", cache.get("HighCharts", "desc two", VER));
    }

    // --- evict ---

    @Test
    void evict_removesEntry() {
        cache.put("HighCharts", "desc", "{}", VER);
        cache.evict("HighCharts", "desc");
        assertNull(cache.get("HighCharts", "desc", VER));
    }

    @Test
    void evict_doesNotAffectOtherEntries() {
        cache.put("HighCharts", "desc1", "{\"a\":1}", VER);
        cache.put("HighCharts", "desc2", "{\"b\":2}", VER);
        cache.evict("HighCharts", "desc1");
        assertNull(cache.get("HighCharts", "desc1", VER));
        assertNotNull(cache.get("HighCharts", "desc2", VER));
    }

    @Test
    void evict_nonExistentEntry_doesNotThrow() {
        assertDoesNotThrow(() -> cache.evict("HighCharts", "does not exist"));
    }

    // --- drop ---

    @Test
    void drop_withLibrary_removesOnlyThatLibrary() {
        cache.put("HighCharts", "desc", "{\"a\":1}", VER);
        cache.put("eCharts", "desc", "{\"b\":2}", VER);
        cache.drop("HighCharts");
        assertNull(cache.get("HighCharts", "desc", VER));
        assertNotNull(cache.get("eCharts", "desc", VER));
    }

    @Test
    void drop_withNull_removesAll() {
        cache.put("HighCharts", "desc1", "{\"a\":1}", VER);
        cache.put("eCharts", "desc2", "{\"b\":2}", VER);
        cache.drop(null);
        assertNull(cache.get("HighCharts", "desc1", VER));
        assertNull(cache.get("eCharts", "desc2", VER));
    }

    @Test
    void drop_withBlankString_removesAll() {
        cache.put("HighCharts", "desc", "{}", VER);
        cache.drop("  ");
        assertNull(cache.get("HighCharts", "desc", VER));
    }

    @Test
    void drop_emptyCache_doesNotThrow() {
        assertDoesNotThrow(() -> cache.drop("HighCharts"));
        assertDoesNotThrow(() -> cache.drop(null));
    }
}
