package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyException;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.ChartInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataRequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataSeriesInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.nl.options.NlOptionsService;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.services.NamedParametersValidationException;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies a namedParameters validation failure is surfaced as 400 Bad Request with the
 * specific reason.
 */
public class RequestBodyHandlerNamedParametersTest {

    private StatsService statsService;
    private RequestBodyHandler handler;

    @BeforeEach
    void setup() {
        statsService = mock(StatsService.class);
        handler = new RequestBodyHandler(statsService, mock(NlQueryService.class), mock(NlOptionsService.class));
    }

    private Query dslQuery() {
        return new Query("named-query", null, null, null, null, null, 0, null, false);
    }

    private RequestInfo singleChartRequest(Query q) {
        ChartInfo ci = new ChartInfo("bar", q);
        ci.setChartName("test");
        return new RequestInfo("HighCharts", List.of(ci), null, false);
    }

    @Test
    void handleRequest_namedParametersValidationFailure_returnsBadRequestWithMessage() throws Exception {
        String message = "Named query 'named-query' is missing required parameter(s): [param2]";
        when(statsService.query(any(), any()))
                .thenThrow(new StatsServiceException(new NamedParametersValidationException(message)));

        RequestBodyException ex = assertThrows(RequestBodyException.class,
                () -> handler.handleRequest(singleChartRequest(dslQuery())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(message, ex.getMessage());
    }

    @Test
    void handleRawDataRequest_namedParametersValidationFailure_returnsBadRequestWithMessage() throws Exception {
        String message = "Query 'named-query' specifies both 'parameters' and 'namedParameters'; use only one.";
        when(statsService.query(any(), any()))
                .thenThrow(new StatsServiceException(new NamedParametersValidationException(message)));

        RawDataRequestInfo request = new RawDataRequestInfo(List.of(new RawDataSeriesInfo(dslQuery())), null, false);

        RequestBodyException ex = assertThrows(RequestBodyException.class,
                () -> handler.handleRawDataRequest(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(message, ex.getMessage());
    }

    @Test
    void handleRequest_otherStatsServiceException_keepsUnprocessableEntity() throws Exception {
        when(statsService.query(any(), any()))
                .thenThrow(new StatsServiceException(new RuntimeException("some unrelated DB failure")));

        RequestBodyException ex = assertThrows(RequestBodyException.class,
                () -> handler.handleRequest(singleChartRequest(dslQuery())));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getHttpStatus());
    }
}
