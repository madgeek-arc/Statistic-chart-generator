package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

import com.fasterxml.jackson.databind.JsonNode;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NlOptionsServiceTest {

    private NlOptionsGenerator optionsGenerator;
    private NlOptionsCache nlOptionsCache;
    private NlRequestSigner signer;
    private NlOptionsService service;

    private static final String VER = "1";

    @BeforeEach
    void setup() {
        optionsGenerator = mock(NlOptionsGenerator.class);
        nlOptionsCache = mock(NlOptionsCache.class);
        signer = new NlRequestSigner("test-secret");
        service = new NlOptionsService(optionsGenerator, nlOptionsCache, signer, VER);
    }

    // --- verifySignature ---

    @Test
    void verifySignature_validSig_doesNotThrow() {
        String sig = signer.sign("HighCharts", "blue bar chart with red title");
        assertDoesNotThrow(() ->
                service.verifySignature("HighCharts", "blue bar chart with red title", sig));
    }

    @Test
    void verifySignature_invalidSig_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                service.verifySignature("HighCharts", "blue bar chart", "badsig"));
    }

    @Test
    void verifySignature_wrongLibrary_throwsSecurityException() {
        String sig = signer.sign("HighCharts", "blue bar chart");
        assertThrows(SecurityException.class, () ->
                service.verifySignature("eCharts", "blue bar chart", sig));
    }

    // --- execute: cache hit ---

    @Test
    void execute_cacheHit_doesNotCallGenerator() {
        when(nlOptionsCache.get("HighCharts", "blue bar chart", VER))
                .thenReturn("{\"colors\":[\"blue\"]}");

        service.execute("HighCharts", "blue bar chart");

        verify(optionsGenerator, never()).generate(any(), any());
    }

    @Test
    void execute_cacheHit_returnsJsonNode() {
        when(nlOptionsCache.get("HighCharts", "blue chart", VER))
                .thenReturn("{\"title\":{\"text\":\"My Chart\"}}");

        JsonNode result = service.execute("HighCharts", "blue chart");

        assertNotNull(result);
        assertEquals("My Chart", result.get("title").get("text").asText());
    }

    // --- execute: cache miss ---

    @Test
    void execute_cacheMiss_callsGeneratorAndCaches() {
        when(nlOptionsCache.get(any(), any(), any())).thenReturn(null);
        when(optionsGenerator.generate(any(), any())).thenReturn("{\"a\":1}");

        service.execute("HighCharts", "some description");

        verify(optionsGenerator).generate("HighCharts", "some description");
        verify(nlOptionsCache).put(eq("HighCharts"), eq("some description"), eq("{\"a\":1}"), eq(VER));
    }

    @Test
    void execute_cacheMiss_returnsJsonNode() {
        when(nlOptionsCache.get(any(), any(), any())).thenReturn(null);
        when(optionsGenerator.generate(any(), any())).thenReturn("{\"b\":2}");

        JsonNode result = service.execute("HighCharts", "some description");

        assertNotNull(result);
        assertEquals(2, result.get("b").asInt());
    }

    // --- execute: corrupt cache entry ---

    @Test
    void execute_corruptCacheEntry_regenerates() {
        when(nlOptionsCache.get(any(), any(), any())).thenReturn("not valid json{{");
        when(optionsGenerator.generate(any(), any())).thenReturn("{\"ok\":true}");

        JsonNode result = service.execute("HighCharts", "some description");

        verify(optionsGenerator).generate(any(), any());
        assertNotNull(result);
        assertTrue(result.get("ok").asBoolean());
    }
}
