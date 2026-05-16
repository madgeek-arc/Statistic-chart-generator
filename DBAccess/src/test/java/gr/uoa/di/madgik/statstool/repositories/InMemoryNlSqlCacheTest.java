package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryNlSqlCacheTest {

    private InMemoryNlSqlCache cache;

    @BeforeEach
    void setup() {
        cache = new InMemoryNlSqlCache();
    }

    @Test
    void get_onEmptyCache_returnsNull() {
        assertNull(cache.get("openaire", "publications per year"));
    }

    @Test
    void put_thenGet_returnsSameValue() {
        QueryWithParameters qwp = new QueryWithParameters("SELECT COUNT(*) FROM result", List.of(), "openaire.public");
        cache.put("openaire", "publications per year", qwp);
        QueryWithParameters result = cache.get("openaire", "publications per year");
        assertNotNull(result);
        assertEquals("SELECT COUNT(*) FROM result", result.getQuery());
    }

    @Test
    void differentProfiles_doNotCollide() {
        QueryWithParameters a = new QueryWithParameters("SELECT 1 FROM result", List.of(), "profile_a");
        QueryWithParameters b = new QueryWithParameters("SELECT 2 FROM result", List.of(), "profile_b");
        cache.put("profile_a", "same nl", a);
        cache.put("profile_b", "same nl", b);

        assertEquals("SELECT 1 FROM result", cache.get("profile_a", "same nl").getQuery());
        assertEquals("SELECT 2 FROM result", cache.get("profile_b", "same nl").getQuery());
    }

    @Test
    void differentNlStrings_doNotCollide() {
        QueryWithParameters a = new QueryWithParameters("SELECT 1 FROM result", List.of(), "p");
        QueryWithParameters b = new QueryWithParameters("SELECT 2 FROM result", List.of(), "p");
        cache.put("openaire", "query one", a);
        cache.put("openaire", "query two", b);

        assertEquals("SELECT 1 FROM result", cache.get("openaire", "query one").getQuery());
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "query two").getQuery());
    }

    @Test
    void put_overwrites_previousValue() {
        QueryWithParameters v1 = new QueryWithParameters("SELECT 1 FROM result", List.of(), "p");
        QueryWithParameters v2 = new QueryWithParameters("SELECT 2 FROM result", List.of(), "p");
        cache.put("openaire", "nl", v1);
        cache.put("openaire", "nl", v2);
        assertEquals("SELECT 2 FROM result", cache.get("openaire", "nl").getQuery());
    }

    @Test
    void parametersArePreserved() {
        QueryWithParameters qwp = new QueryWithParameters("SELECT * FROM result WHERE year=?", List.of("2023"), "p");
        cache.put("openaire", "results in 2023", qwp);
        assertEquals(List.of("2023"), cache.get("openaire", "results in 2023").getParameters());
    }
}
