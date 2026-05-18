package gr.uoa.di.madgik.ChartDataFormatter.nl.mcp;

import gr.uoa.di.madgik.ChartDataFormatter.nl.NlSqlGenerator;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchemaBuilder;
import gr.uoa.di.madgik.ChartDataFormatter.nl.SqlResult;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NlMcpToolsTest {

    private Mapper mapper;
    private StatsService statsService;
    private NlRequestSigner signer;
    private NlSqlGenerator sqlGenerator;
    private NlSqlCache nlSqlCache;
    private NlMcpTools tools;

    private ProfileConfiguration profileConfig;

    @BeforeEach
    void setup() {
        mapper = mock(Mapper.class);
        statsService = mock(StatsService.class);
        signer = new NlRequestSigner("test-secret");
        sqlGenerator = mock(NlSqlGenerator.class);
        nlSqlCache = mock(NlSqlCache.class);

        tools = new NlMcpTools(mapper, statsService, signer, sqlGenerator, nlSqlCache,
                new ProfileSchemaBuilder(mapper), "/chart/json");

        profileConfig = new ProfileConfiguration();
        profileConfig.tables = new HashMap<>();
        profileConfig.tables.put("result", new Table("result", "id", null));
        profileConfig.fields = new HashMap<>();
        profileConfig.relations = new HashMap<>();

        when(mapper.getProfileConfiguration("openaire")).thenReturn(profileConfig);
        when(mapper.getEntities("openaire")).thenReturn(new HashMap<>());
    }

    // --- signNlQuery: happy path ---

    @Test
    void signNlQuery_happyPath_returnsUrl() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", List.of()));

        NlMcpTools.clearSignedQuery();
        String result = tools.signNlQuery("openaire", "total publications");

        assertTrue(result.contains("/chart/json"), "should return chart URL");
        assertTrue(result.contains("openaire"), "URL should contain profile");
    }

    @Test
    void signNlQuery_happyPath_populatesThreadLocal() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", List.of()));

        NlMcpTools.clearSignedQuery();
        tools.signNlQuery("openaire", "total publications");

        NlMcpTools.SignedQuery signed = NlMcpTools.consumeSignedQuery();
        assertNotNull(signed);
        assertEquals("total publications", signed.canonicalNl());
        assertNotNull(signed.sig());
        assertEquals("SELECT COUNT(*) FROM result", signed.sql());
    }

    @Test
    void signNlQuery_happyPath_cachesGeneratedSql() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", List.of("pub")));

        NlMcpTools.clearSignedQuery();
        tools.signNlQuery("openaire", "total publications");

        verify(nlSqlCache).put(eq("openaire"), eq("total publications"), argThat(qwp ->
                "SELECT COUNT(*) FROM result".equals(qwp.getQuery()) &&
                List.of("pub").equals(qwp.getParameters())
        ), anyString());
    }

    @Test
    void signNlQuery_happyPath_signatureIsVerifiable() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", List.of()));

        NlMcpTools.clearSignedQuery();
        tools.signNlQuery("openaire", "total publications");

        NlMcpTools.SignedQuery signed = NlMcpTools.consumeSignedQuery();
        assertTrue(signer.verify("openaire", "total publications", signed.sig()));
    }

    // --- signNlQuery: SQL generation failure ---

    @Test
    void signNlQuery_generationFails_returnsErrorString() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenThrow(new IllegalStateException("LLM parse error"));

        NlMcpTools.clearSignedQuery();
        String result = tools.signNlQuery("openaire", "ambiguous query");

        assertTrue(result.startsWith("ERROR:"), "should return error message to LLM");
    }

    @Test
    void signNlQuery_generationFails_doesNotCache() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenThrow(new IllegalStateException("fail"));

        NlMcpTools.clearSignedQuery();
        tools.signNlQuery("openaire", "ambiguous query");

        verify(nlSqlCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void signNlQuery_generationFails_doesNotPopulateThreadLocal() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenThrow(new IllegalStateException("fail"));

        NlMcpTools.clearSignedQuery();
        tools.signNlQuery("openaire", "ambiguous query");

        assertNull(NlMcpTools.consumeSignedQuery());
    }

    // --- signNlQuery: SQL validation failure ---

    @Test
    void signNlQuery_unknownTable_returnsErrorString() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT * FROM unknown_table", List.of()));

        NlMcpTools.clearSignedQuery();
        String result = tools.signNlQuery("openaire", "query unknown table");

        assertTrue(result.startsWith("ERROR:"));
        verify(nlSqlCache, never()).put(any(), any(), any(), any());
        assertNull(NlMcpTools.consumeSignedQuery());
    }

    @Test
    void signNlQuery_ddlSql_returnsErrorString() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("DROP TABLE result", List.of()));

        NlMcpTools.clearSignedQuery();
        String result = tools.signNlQuery("openaire", "drop tables");

        assertTrue(result.startsWith("ERROR:"));
        verify(nlSqlCache, never()).put(any(), any(), any(), any());
    }

    // --- thread-local lifecycle ---

    @Test
    void clearSignedQuery_preventsConsuming() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", List.of()));
        tools.signNlQuery("openaire", "nl");

        NlMcpTools.clearSignedQuery();

        assertNull(NlMcpTools.consumeSignedQuery());
    }

    @Test
    void consumeSignedQuery_clearsThreadLocal() {
        when(sqlGenerator.generate(any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", List.of()));
        NlMcpTools.clearSignedQuery();
        tools.signNlQuery("openaire", "nl");

        NlMcpTools.consumeSignedQuery(); // first consume
        assertNull(NlMcpTools.consumeSignedQuery()); // second should be null
    }

    // --- validateSql ---

    @Test
    void validateSql_validSelect_returnsOk() {
        String result = tools.validateSql("openaire", "SELECT COUNT(*) FROM result");
        assertEquals("OK", result);
    }

    @Test
    void validateSql_unknownTable_returnsInvalid() {
        String result = tools.validateSql("openaire", "SELECT * FROM unknown_table");
        assertTrue(result.startsWith("INVALID:"));
    }

    @Test
    void validateSql_ddlStatement_returnsInvalid() {
        String result = tools.validateSql("openaire", "DROP TABLE result");
        assertTrue(result.startsWith("INVALID:"));
    }

    // --- getProfiles ---

    @Test
    void getProfiles_returnsProfilesFromMapper() {
        var profiles = List.of(
                new gr.uoa.di.madgik.statstool.mapping.domain.Profile("openaire", "OpenAIRE stats", null, null, 0),
                new gr.uoa.di.madgik.statstool.mapping.domain.Profile("monitor", "Monitor stats", null, null, 0)
        );
        when(mapper.getProfiles()).thenReturn(profiles);

        List<Map<String, String>> result = tools.getProfiles();

        assertEquals(2, result.size());
        assertEquals("openaire", result.get(0).get("name"));
        assertEquals("OpenAIRE stats", result.get(0).get("description"));
        assertEquals("monitor", result.get(1).get("name"));
    }

    @Test
    void getProfiles_nullDescription_treatedAsEmpty() {
        when(mapper.getProfiles()).thenReturn(
                List.of(new gr.uoa.di.madgik.statstool.mapping.domain.Profile("p", null, null, null, 0))
        );

        List<Map<String, String>> result = tools.getProfiles();
        assertEquals("", result.get(0).get("description"));
    }

    // --- getFieldValues ---

    @Test
    void getFieldValues_serviceThrows_returnsEmptyList() throws Exception {
        when(statsService.query(any())).thenThrow(new RuntimeException("db error"));

        List<String> result = tools.getFieldValues("openaire", "result.type", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void getFieldValues_emptyResult_returnsEmptyList() throws Exception {
        when(statsService.query(any())).thenReturn(List.of(new Result()));

        List<String> result = tools.getFieldValues("openaire", "result.type", 5);

        assertTrue(result.isEmpty());
    }
}
