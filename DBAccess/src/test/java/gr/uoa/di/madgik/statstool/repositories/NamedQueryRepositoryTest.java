package gr.uoa.di.madgik.statstool.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NamedQueryRepositoryTest {

    private NamedQueryRepository repo;

    @BeforeEach
    void setup() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("plain.query", "select 1");
        queries.put("shared.list", "'AT','BE','SI'");
        queries.put("with.placeholder", "select * from t where country in (${shared.list})");
        queries.put("with.undefined", "select * from t where x = ${does.not.exist}");

        ConfigRepository configRepository = mock(ConfigRepository.class);
        when(configRepository.loadNamedQueries()).thenReturn(queries);

        repo = new NamedQueryRepository();
        repo.configRepository = configRepository;
        repo.init();
    }

    @Test
    void returnsQueryUnchanged_whenNoPlaceholder() {
        assertEquals("select 1", repo.getQuery("plain.query"));
    }

    @Test
    void resolvesPlaceholder_againstAnotherEntry() {
        assertEquals(
            "select * from t where country in ('AT','BE','SI')",
            repo.getQuery("with.placeholder"));
    }

    @Test
    void leavesUndefinedPlaceholderAsIs() {
        assertEquals(
            "select * from t where x = ${does.not.exist}",
            repo.getQuery("with.undefined"));
    }

    @Test
    void returnsNull_whenNameNotFound() {
        assertNull(repo.getQuery("missing.name"));
    }
}
