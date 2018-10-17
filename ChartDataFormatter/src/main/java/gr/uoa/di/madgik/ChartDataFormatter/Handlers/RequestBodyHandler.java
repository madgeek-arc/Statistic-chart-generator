package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.GoogleChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the Request Body and propagates the workload to the correct {@link DataFormatter}.
 */
@Service
public class RequestBodyHandler {

    private StatsService statsService;
    private final Logger log = Logger.getLogger(this.getClass());

    public RequestBodyHandler(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * @param requestJson Holds the appropriate info to correctly format the queried data.
     * @return The appropriate {@link JsonResponse} depending on the value of the Library in the {@link RequestInfo}.
     * @throws RequestBodyException
     */
    public JsonResponse handleRequest(RequestInfo requestJson) throws RequestBodyException {

        List<Result> statsServiceResults = this.statsService.query(requestJson.getChartQueries());

        if (statsServiceResults == null)
            throw new RequestBodyException("Stats Service Error", HttpStatus.INTERNAL_SERVER_ERROR);

        try {

            if(log.isInfoEnabled()) {
                log.info("Chart Types: " + requestJson.getChartTypes());
                log.info("Chart Names: " + requestJson.getChartNames());

                for (int i = 0; i < statsServiceResults.size(); i++) {
                    Result res = statsServiceResults.get(i);
                    log.info("Stats Service Results [" + i + "]: " + res.getRows().toString());
                }
            }

            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case HighCharts:

                    HighChartsJsonResponse highChartsJsonResponse;
                    try {
                        highChartsJsonResponse = new HighChartsDataFormatter().toJsonResponse(statsServiceResults,
                                requestJson.getChartTypes(), requestJson.getChartNames());

                    }catch (DataFormatter.DataFormationException e){
                        throw new RequestBodyException(e.getMessage(),e,HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(highChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    if(log.isInfoEnabled()) {
                        if(highChartsJsonResponse.getDataSeriesNames() != null)
                            log.info(highChartsJsonResponse.getDataSeriesNames().toString());
                        if(highChartsJsonResponse.getDataSeriesTypes() != null)
                            log.info(highChartsJsonResponse.getDataSeriesTypes().toString());
                        if(highChartsJsonResponse.getxAxis_categories() != null)
                            log.info(highChartsJsonResponse.getxAxis_categories().toString());
                        if(highChartsJsonResponse.getDataSeries() != null) {
                            log.info(highChartsJsonResponse.getDataSeries().toString());
                            if(highChartsJsonResponse.getDataSeries().size() > 0 )
                                log.info("DataSeries row size: " + ((ArrayList<Number>) highChartsJsonResponse.getDataSeries().get(0).getData()).size());
                        }
                    }

                    return highChartsJsonResponse;

                case GoogleCharts:

                    GoogleChartsJsonResponse googleChartsJsonResponse;
                    try{
                        googleChartsJsonResponse = new GoogleChartsDataFormatter().toJsonResponse(statsServiceResults,
                                requestJson.getChartTypes(), requestJson.getChartNames());

                    } catch (DataFormatter.DataFormationException e) {
                        throw new RequestBodyException(e.getMessage(),e, HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(googleChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    return googleChartsJsonResponse;

                default:
                    throw new RequestBodyException("Chart Library not supported yet",HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (RuntimeException e){
            throw new RequestBodyException("Chart Data Formation Error", e, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * An exception holding a HttpStatus code along with the error message.
     */
    public class RequestBodyException extends Exception{

        private final HttpStatus httpStatus;

        public HttpStatus getHttpStatus() { return httpStatus; }

        public RequestBodyException(HttpStatus httpStatus)
        { super(); this.httpStatus = httpStatus;}
        public RequestBodyException(String s, HttpStatus httpStatus)
        { super(s); this.httpStatus = httpStatus;}
        public RequestBodyException(String s, Throwable throwable, HttpStatus httpStatus)
        { super(s, throwable); this.httpStatus = httpStatus;}
        public RequestBodyException(Throwable throwable, HttpStatus httpStatus)
        { super(throwable); this.httpStatus = httpStatus;}

    }
}
