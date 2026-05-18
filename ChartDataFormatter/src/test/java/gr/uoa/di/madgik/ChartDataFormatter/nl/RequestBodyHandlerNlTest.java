package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyException;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.ChartInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataRequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataSeriesInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RequestBodyHandlerNlTest {

    private StatsService statsService;
    private NlQueryService nlQueryService;
    private RequestBodyHandler handler;

    @BeforeEach
    void setup() {
        statsService = mock(StatsService.class);
        nlQueryService = mock(NlQueryService.class);
        handler = new RequestBodyHandler(statsService, nlQueryService);
    }

    private Query nlQuery(String nl, String sig, String profile) {
        Query q = new Query();
        q.setNl(nl);
        q.setSig(sig);
        q.setProfile(profile);
        return q;
    }

    private Query dslQuery() {
        return new Query("named-query", null, null, null, null, null, 0, null, false);
    }

    private Result twoColumnResult() {
        Result r = new Result();
        r.addRow(Arrays.asList("2020", 100));
        r.addRow(Arrays.asList("2021", 200));
        return r;
    }

    private RequestInfo singleChartRequest(Query q) {
        ChartInfo ci = new ChartInfo("bar", q);
        ci.setChartName("test");
        return new RequestInfo("HighCharts", List.of(ci), null, false);
    }

    // --- handleRequest: NL dispatch ---

    @Test
    void handleRequest_nlQuery_verifiesSignatureAndExecutes() throws Exception {
        Query q = nlQuery("publications per year", "sig", "openaire");
        when(nlQueryService.execute("openaire", "publications per year")).thenReturn(twoColumnResult());

        handler.handleRequest(singleChartRequest(q));

        verify(nlQueryService).verifySignature("openaire", "publications per year", "sig");
        verify(nlQueryService).execute("openaire", "publications per year");
        verifyNoInteractions(statsService);
    }

    @Test
    void handleRequest_dslQuery_callsStatsService() throws Exception {
        Query q = dslQuery();
        when(statsService.query(any(), any())).thenReturn(List.of(twoColumnResult()));

        handler.handleRequest(singleChartRequest(q));

        verify(statsService).query(any(), any());
        verifyNoInteractions(nlQueryService);
    }

    @Test
    void handleRequest_invalidSignature_returnsForbidden() throws Exception {
        Query q = nlQuery("publications per year", "badsig", "openaire");
        doThrow(new SecurityException("bad sig"))
                .when(nlQueryService).verifySignature(any(), any(), any());

        RequestBodyException ex = assertThrows(RequestBodyException.class,
                () -> handler.handleRequest(singleChartRequest(q)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        verify(nlQueryService, never()).execute(any(), any());
    }

    @Test
    void handleRequest_mixedNlAndDsl_dispatchesCorrectly() throws Exception {
        Query nlQ = nlQuery("publications per year", "sig", "openaire");
        Query dslQ = dslQuery();

        ChartInfo nlChart = new ChartInfo("bar", nlQ);
        nlChart.setChartName("nl chart");
        ChartInfo dslChart = new ChartInfo("bar", dslQ);
        dslChart.setChartName("dsl chart");

        RequestInfo request = new RequestInfo("HighCharts", List.of(nlChart, dslChart), null, false);

        when(nlQueryService.execute("openaire", "publications per year")).thenReturn(twoColumnResult());
        when(statsService.query(any(), any())).thenReturn(List.of(twoColumnResult()));

        handler.handleRequest(request);

        verify(nlQueryService).execute("openaire", "publications per year");
        verify(statsService).query(argThat(list -> list.size() == 1), any());
    }

    @Test
    void handleRequest_multipleNlQueries_allVerifiedAndExecuted() throws Exception {
        Query q1 = nlQuery("publications per year", "sig1", "openaire");
        Query q2 = nlQuery("datasets per year", "sig2", "openaire");

        ChartInfo c1 = new ChartInfo("bar", q1); c1.setChartName("c1");
        ChartInfo c2 = new ChartInfo("bar", q2); c2.setChartName("c2");

        RequestInfo request = new RequestInfo("HighCharts", List.of(c1, c2), null, false);

        when(nlQueryService.execute("openaire", "publications per year")).thenReturn(twoColumnResult());
        when(nlQueryService.execute("openaire", "datasets per year")).thenReturn(twoColumnResult());

        handler.handleRequest(request);

        verify(nlQueryService).execute("openaire", "publications per year");
        verify(nlQueryService).execute("openaire", "datasets per year");
        verifyNoInteractions(statsService);
    }

    // --- handleRawDataRequest: NL dispatch ---

    @Test
    void handleRawDataRequest_nlQuery_verifiesSignatureAndExecutes() throws Exception {
        Query q = nlQuery("publications per year", "sig", "openaire");
        RawDataRequestInfo request = new RawDataRequestInfo(
                List.of(new RawDataSeriesInfo(q)), null, false);

        when(nlQueryService.execute("openaire", "publications per year")).thenReturn(twoColumnResult());

        handler.handleRawDataRequest(request);

        verify(nlQueryService).verifySignature("openaire", "publications per year", "sig");
        verify(nlQueryService).execute("openaire", "publications per year");
        verifyNoInteractions(statsService);
    }

    @Test
    void handleRawDataRequest_dslQuery_callsStatsService() throws Exception {
        Query q = dslQuery();
        RawDataRequestInfo request = new RawDataRequestInfo(
                List.of(new RawDataSeriesInfo(q)), null, false);

        when(statsService.query(any(), any())).thenReturn(List.of(twoColumnResult()));

        handler.handleRawDataRequest(request);

        verify(statsService).query(any(), any());
        verifyNoInteractions(nlQueryService);
    }

    @Test
    void handleRawDataRequest_invalidSignature_returnsForbidden() throws Exception {
        Query q = nlQuery("publications per year", "badsig", "openaire");
        RawDataRequestInfo request = new RawDataRequestInfo(
                List.of(new RawDataSeriesInfo(q)), null, false);

        doThrow(new SecurityException("bad sig"))
                .when(nlQueryService).verifySignature(any(), any(), any());

        RequestBodyException ex = assertThrows(RequestBodyException.class,
                () -> handler.handleRawDataRequest(request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void handleRawDataRequest_mixedNlAndDsl_dispatchesCorrectly() throws Exception {
        Query nlQ = nlQuery("publications per year", "sig", "openaire");
        Query dslQ = dslQuery();

        RawDataRequestInfo request = new RawDataRequestInfo(
                List.of(new RawDataSeriesInfo(nlQ), new RawDataSeriesInfo(dslQ)), null, false);

        when(nlQueryService.execute("openaire", "publications per year")).thenReturn(twoColumnResult());
        when(statsService.query(any(), any())).thenReturn(List.of(twoColumnResult()));

        handler.handleRawDataRequest(request);

        verify(nlQueryService).execute("openaire", "publications per year");
        verify(statsService).query(argThat(list -> list.size() == 1), any());
    }
}
