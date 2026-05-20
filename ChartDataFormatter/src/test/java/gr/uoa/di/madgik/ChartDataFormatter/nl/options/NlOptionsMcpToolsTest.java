package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

import gr.uoa.di.madgik.ChartDataFormatter.nl.options.mcp.NlOptionsMcpTools;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NlOptionsMcpToolsTest {

    private NlRequestSigner signer;
    private NlOptionsCache cache;
    private NlOptionsMcpTools tools;

    private static final String VER = "1";
    private static final String VALID_JSON = "{\"colors\":[\"#003399\"],\"title\":{\"text\":\"Test\"}}";

    @BeforeEach
    void setup() {
        signer = new NlRequestSigner("test-secret");
        cache = mock(NlOptionsCache.class);
        tools = new NlOptionsMcpTools(signer, cache, VER);
        NlOptionsMcpTools.clearSignedOptions();
    }

    // --- previewOptions ---

    @Test
    void previewOptions_validJson_returnsPrettyPrinted() {
        String result = tools.previewOptions(VALID_JSON);
        assertNotNull(result);
        assertTrue(result.contains("colors"));
        assertTrue(result.contains("title"));
        assertFalse(result.startsWith("INVALID"));
    }

    @Test
    void previewOptions_invalidJson_returnsErrorMessage() {
        String result = tools.previewOptions("not json at all {{{");
        assertTrue(result.startsWith("INVALID JSON:"));
    }

    @Test
    void previewOptions_emptyObject_returnsPrettyPrinted() {
        String result = tools.previewOptions("{}");
        assertFalse(result.startsWith("INVALID"));
        assertTrue(result.contains("{"));
    }

    // --- signChartOptions ---

    @Test
    void signChartOptions_validJson_returnsSuccessMessage() {
        String result = tools.signChartOptions("HighCharts", "blue bar chart", VALID_JSON);
        assertEquals("Chart options signed successfully.", result);
    }

    @Test
    void signChartOptions_validJson_storesInThreadLocal() {
        tools.signChartOptions("HighCharts", "blue bar chart", VALID_JSON);

        NlOptionsMcpTools.SignedOptions signed = NlOptionsMcpTools.consumeSignedOptions();
        assertNotNull(signed);
        assertEquals("HighCharts", signed.library());
        assertEquals("blue bar chart", signed.canonicalDescription());
        assertEquals(VALID_JSON, signed.optionsJson());
        assertNotNull(signed.sig());
    }

    @Test
    void signChartOptions_validJson_sigIsVerifiable() {
        tools.signChartOptions("HighCharts", "blue bar chart", VALID_JSON);

        NlOptionsMcpTools.SignedOptions signed = NlOptionsMcpTools.consumeSignedOptions();
        assertNotNull(signed);
        assertTrue(signer.verify("HighCharts", "blue bar chart", signed.sig()));
    }

    @Test
    void signChartOptions_validJson_cachesWithPromptVersion() {
        tools.signChartOptions("HighCharts", "blue bar chart", VALID_JSON);

        verify(cache).put("HighCharts", "blue bar chart", VALID_JSON, VER);
    }

    @Test
    void signChartOptions_invalidJson_returnsError() {
        String result = tools.signChartOptions("HighCharts", "blue bar", "not-json{{{");
        assertTrue(result.startsWith("ERROR: Invalid JSON:"));
    }

    @Test
    void signChartOptions_invalidJson_doesNotSetThreadLocal() {
        tools.signChartOptions("HighCharts", "blue bar", "not-json{{{");
        assertNull(NlOptionsMcpTools.consumeSignedOptions());
    }

    // --- consumeSignedOptions ---

    @Test
    void consumeSignedOptions_clearsThreadLocalAfterRead() {
        tools.signChartOptions("HighCharts", "desc", VALID_JSON);

        NlOptionsMcpTools.consumeSignedOptions(); // first read
        assertNull(NlOptionsMcpTools.consumeSignedOptions()); // second read returns null
    }

    @Test
    void clearSignedOptions_clearsThreadLocal() {
        tools.signChartOptions("HighCharts", "desc", VALID_JSON);
        NlOptionsMcpTools.clearSignedOptions();
        assertNull(NlOptionsMcpTools.consumeSignedOptions());
    }
}
