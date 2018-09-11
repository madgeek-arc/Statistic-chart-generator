package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.GoogleChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.ArrayOfValues;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the Request Body and propagates the workload to the correct {@link DataFormatter}.
 */
@Service
public class RequestBodyHandler {

    private StatsService statsService;

    private static boolean DEBUGMODE = true;

    public RequestBodyHandler(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * @param requestJson Holds the appropriate info to correctly format the queried data.
     * @return The appropriate {@link JsonResponse} depending on the value of the Library in the {@link RequestInfo}.
     * @throws RequestBodyException
     */
    public JsonResponse handleRequest(RequestInfo requestJson) throws RequestBodyException {

        if(DEBUGMODE)
            System.out.println("Chart types: " + requestJson.getChartTypes());

        List<Result> statsServiceResults = this.statsService.query(requestJson.getChartQueries());

        if (statsServiceResults == null)
            throw new RequestBodyException("Stats Service Error", HttpStatus.INTERNAL_SERVER_ERROR);
        int resultNo = 0;
        for(Result res : statsServiceResults) {
            System.out.println("Stats Service Results ["+resultNo+"]: " + res.getRows().toString());
            resultNo++;
        }

        try {
            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case HighCharts:

                    HighChartsJsonResponse highChartsJsonResponse;
                    try {
                        highChartsJsonResponse = new HighChartsDataFormatter().toJsonResponse(statsServiceResults,requestJson.getChartTypes());

                    }catch (DataFormatter.DataFormationException e){
                        e.printStackTrace();
                        throw new RequestBodyException("Results and chart types were not matched 1-1",HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(highChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    if(DEBUGMODE) {
                        if(highChartsJsonResponse.getDataSeriesNames() != null)
                            System.out.println(highChartsJsonResponse.getDataSeriesNames().toString());
                        if(highChartsJsonResponse.getDataSeriesTypes() != null)
                            System.out.println(highChartsJsonResponse.getDataSeriesTypes().toString());
                        if(highChartsJsonResponse.getxAxis_categories() != null)
                            System.out.println(highChartsJsonResponse.getxAxis_categories().toString());
                        if(highChartsJsonResponse.getDataSeries() != null) {
                            System.out.println(highChartsJsonResponse.getDataSeries().toString());
                            System.out.println("DataSeries row size: " + ((ArrayList<Number>) highChartsJsonResponse.getDataSeries().get(0).getData()).size());
                        }
                    }

                    return highChartsJsonResponse;

                case GoogleCharts:

                    GoogleChartsJsonResponse googleChartsJsonResponse;
                    try{
                        //Google Charts data is independent of type, hence chartsType = null
                        googleChartsJsonResponse = new GoogleChartsDataFormatter().toJsonResponse(statsServiceResults,null);

                    } catch (DataFormatter.DataFormationException e) {
                        e.printStackTrace();
                        throw new RequestBodyException(HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(googleChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    return googleChartsJsonResponse;

                default:
                    throw new RequestBodyException("Chart Library not supported yet",HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (RuntimeException e){
            e.printStackTrace();
            throw new RequestBodyException("Chart Data Formation Error" ,HttpStatus.UNPROCESSABLE_ENTITY);
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
