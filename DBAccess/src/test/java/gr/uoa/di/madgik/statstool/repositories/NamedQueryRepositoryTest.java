package gr.uoa.di.madgik.statstool.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NamedQueryRepositoryTest {

    private NamedQueryRepository repo;

    @BeforeEach
    void setup() throws Exception {
        String properties = String.join("\n",
                "plain.query=select 1",
                "shared.list='AT','BE','SI'",
                "with.placeholder=select * from t where country in (${shared.list})",
                "with.undefined=select * from t where x = ${does.not.exist}"
        );

        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resourceLoader.getResource(any())).thenReturn(resource);
        when(resource.getInputStream()).thenAnswer(invocation ->
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        repo = new NamedQueryRepository();
        repo.resourceLoader = resourceLoader;
    }

    @Test
    void returnsQueryUnchanged_whenNoPlaceholder() throws Exception {
        assertEquals("select 1", repo.getQuery("plain.query"));
    }

    @Test
    void resolvesPlaceholder_againstAnotherProperty() throws Exception {
        assertEquals(
                "select * from t where country in ('AT','BE','SI')",
                repo.getQuery("with.placeholder"));
    }

    @Test
    void leavesUndefinedPlaceholderAsIs() throws Exception {
        assertEquals(
                "select * from t where x = ${does.not.exist}",
                repo.getQuery("with.undefined"));
    }

    @Test
    void returnsNull_whenNameNotFound() throws Exception {
        assertNull(repo.getQuery("missing.name"));
    }
}
