package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchemaBuilder;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.repositories.NlCachedEntry;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NlQueryServiceTest {

    private NlSqlGenerator sqlGenerator;
    private NlSqlCache nlSqlCache;
    private NlRequestSigner signer;
    private StatsService statsService;
    private Mapper mapper;
    private NlQueryService service;

    private ProfileConfiguration profile;

    @BeforeEach
    void setup() {
        sqlGenerator = mock(NlSqlGenerator.class);
        nlSqlCache = mock(NlSqlCache.class);
        signer = new NlRequestSigner("test-secret");
        statsService = mock(StatsService.class);
        mapper = mock(Mapper.class);

        service = new NlQueryService(sqlGenerator, nlSqlCache, signer, statsService, mapper,
                new ProfileSchemaBuilder(mapper));

        profile = new ProfileConfiguration();
        profile.tables.put("result", new Table("result", "id", null));
        when(mapper.getProfileConfiguration("openaire")).thenReturn(profile);
    }

    // --- verifySignature ---

    @Test
    void verifySignature_validSig_doesNotThrow() {
        String sig = signer.sign("openaire", "publications per year");
        assertDoesNotThrow(() -> service.verifySignature("openaire", "publications per year", sig));
    }

    @Test
    void verifySignature_invalidSig_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                service.verifySignature("openaire", "publications per year", "badsig"));
    }

    @Test
    void verifySignature_wrongProfile_throwsSecurityException() {
        String sig = signer.sign("openaire", "publications per year");
        assertThrows(SecurityException.class, () ->
                service.verifySignature("monitor", "publications per year", sig));
    }

    // --- execute: cache hit ---

    @Test
    void execute_cacheHit_doesNotCallGenerator() throws StatsServiceException {
        QueryWithParameters qwp = new QueryWithParameters(
                "SELECT COUNT(*) FROM result", List.of(), "openaire.public");
        NlCachedEntry cached = new NlCachedEntry(qwp);
        when(nlSqlCache.get(eq("openaire"), eq("publications per year"), anyString())).thenReturn(cached);
        when(statsService.queryRaw(qwp)).thenReturn(new Result());

        service.execute("openaire", "publications per year");

        verify(sqlGenerator, never()).generate(any(), any(), any());
        verify(statsService).queryRaw(qwp);
    }

    @Test
    void execute_cacheHit_returnsResultFromStatsService() throws StatsServiceException {
        Result expected = new Result();
        expected.setRows(List.of(List.of(42L)));
        QueryWithParameters qwp = new QueryWithParameters(
                "SELECT COUNT(*) FROM result", List.of(), "openaire.public");
        NlCachedEntry cached = new NlCachedEntry(qwp);
        when(nlSqlCache.get(eq("openaire"), eq("nl"), anyString())).thenReturn(cached);
        when(statsService.queryRaw(qwp)).thenReturn(expected);

        Result actual = service.execute("openaire", "nl");
        assertSame(expected, actual);
    }

    // --- execute: cache miss ---

    @Test
    void execute_cacheMiss_callsGeneratorAndCaches() throws StatsServiceException {
        when(nlSqlCache.get(any(), any(), any())).thenReturn(null);
        when(sqlGenerator.generate(any(), any(), any(), any()))
                .thenReturn(new SqlResult("SELECT COUNT(*) FROM result", new ArrayList<>()));
        when(statsService.queryRaw(any())).thenReturn(new Result());
        when(mapper.getEntities("openaire")).thenReturn(new java.util.HashMap<>());

        service.execute("openaire", "total results");

        verify(sqlGenerator).generate(eq("total results"), eq("openaire"), any(), any());
        verify(nlSqlCache).put(eq("openaire"), eq("total results"), any(), anyString());
    }

    @Test
    void execute_cacheMiss_executesGeneratedSql() throws StatsServiceException {
        when(nlSqlCache.get(any(), any(), any())).thenReturn(null);
        String generatedSql = "SELECT COUNT(*) FROM result";
        when(sqlGenerator.generate(any(), any(), any(), any()))
                .thenReturn(new SqlResult(generatedSql, List.of("publication")));
        when(statsService.queryRaw(any())).thenReturn(new Result());
        when(mapper.getEntities("openaire")).thenReturn(new java.util.HashMap<>());

        service.execute("openaire", "count publications");

        verify(statsService).queryRaw(argThat(qwp ->
                generatedSql.equals(qwp.getQuery()) &&
                List.of("publication").equals(qwp.getParameters())));
    }

    // --- execute: SQL safety ---

    @Test
    void execute_unsafeSql_throwsAndDoesNotCache() {
        when(nlSqlCache.get(any(), any(), any())).thenReturn(null);
        when(sqlGenerator.generate(any(), any(), any(), any()))
                .thenReturn(new SqlResult("DROP TABLE result", new ArrayList<>()));
        when(mapper.getEntities("openaire")).thenReturn(new java.util.HashMap<>());

        assertThrows(IllegalArgumentException.class, () ->
                service.execute("openaire", "drop tables"));

        verify(nlSqlCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void execute_unknownTableInSql_throwsAndDoesNotCache() {
        when(nlSqlCache.get(any(), any(), any())).thenReturn(null);
        when(sqlGenerator.generate(any(), any(), any(), any()))
                .thenReturn(new SqlResult("SELECT * FROM unknown_table", new ArrayList<>()));
        when(mapper.getEntities("openaire")).thenReturn(new java.util.HashMap<>());

        assertThrows(IllegalArgumentException.class, () ->
                service.execute("openaire", "query unknown table"));

        verify(nlSqlCache, never()).put(any(), any(), any(), any());
    }

    // --- execute: database rejects the generated SQL ---

    private static final String BAD_SQL = "SELECT COUNT(DISTINCT result.id), result.type FROM result";
    private static final String GOOD_SQL = "SELECT COUNT(DISTINCT result.id), result.type FROM result GROUP BY result.type";

    private void stubCacheMissWithGeneratedSql(String sql) {
        when(nlSqlCache.get(any(), any(), any())).thenReturn(null);
        when(mapper.getEntities("openaire")).thenReturn(new java.util.HashMap<>());
        when(sqlGenerator.generate(any(), any(), any(), any()))
                .thenReturn(new SqlResult(sql, new ArrayList<>()));
    }

    @Test
    void execute_dbRejectsSql_noRepair_throwsAndDoesNotCache() throws StatsServiceException {
        stubCacheMissWithGeneratedSql(BAD_SQL);
        when(statsService.queryRaw(any())).thenThrow(new StatsServiceException("AnalysisException: missing from GROUP BY"));

        assertThrows(StatsServiceException.class, () -> service.execute("openaire", "results per type"));

        verify(nlSqlCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void execute_dbRejectsSql_repairedSqlRunsAndIsCached() throws StatsServiceException {
        stubCacheMissWithGeneratedSql(BAD_SQL);
        Result expected = new Result();
        when(statsService.queryRaw(argThat(q -> q != null && BAD_SQL.equals(q.getQuery()))))
                .thenThrow(new StatsServiceException("AnalysisException: missing from GROUP BY"));
        when(statsService.queryRaw(argThat(q -> q != null && GOOD_SQL.equals(q.getQuery()))))
                .thenReturn(expected);
        when(sqlGenerator.repair(eq("results per type"), eq("openaire"), any(), any(),
                argThat(f -> BAD_SQL.equals(f.getSql())), contains("GROUP BY")))
                .thenReturn(new SqlResult(GOOD_SQL, new ArrayList<>(), "Results per type"));

        Result actual = service.execute("openaire", "results per type");

        assertSame(expected, actual);
        verify(nlSqlCache).put(eq("openaire"), eq("results per type"),
                argThat((NlCachedEntry e) -> GOOD_SQL.equals(e.qwp().getQuery())), anyString());
        verify(nlSqlCache, never()).put(any(), any(),
                argThat((NlCachedEntry e) -> BAD_SQL.equals(e.qwp().getQuery())), any());
    }

    @Test
    void execute_repairedSqlAlsoFails_throwsAndDoesNotCache() throws StatsServiceException {
        stubCacheMissWithGeneratedSql(BAD_SQL);
        when(statsService.queryRaw(any())).thenThrow(new StatsServiceException("still broken"));
        when(sqlGenerator.repair(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SqlResult(GOOD_SQL, new ArrayList<>()));

        assertThrows(StatsServiceException.class, () -> service.execute("openaire", "results per type"));

        verify(sqlGenerator, times(1)).repair(any(), any(), any(), any(), any(), any());
        verify(nlSqlCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void execute_invalidSql_isRepairedBeforeRunning() throws StatsServiceException {
        stubCacheMissWithGeneratedSql("SELECT * FROM unknown_table");
        when(sqlGenerator.repair(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SqlResult(GOOD_SQL, new ArrayList<>()));
        when(statsService.queryRaw(any())).thenReturn(new Result());

        service.execute("openaire", "results per type");

        verify(statsService, times(1)).queryRaw(argThat(q -> GOOD_SQL.equals(q.getQuery())));
        verify(nlSqlCache).put(eq("openaire"), eq("results per type"),
                argThat((NlCachedEntry e) -> GOOD_SQL.equals(e.qwp().getQuery())), anyString());
    }
}
