package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.JsonResponse;
import gr.uoa.di.madgik.statstool.db.DBAccess;
import gr.uoa.di.madgik.statstool.db.Result;
import gr.uoa.di.madgik.statstool.query.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the Request Body and propagates the workload to the correct {@link DataFormatter}.
 */
public class RequestBodyHandler {

    /**
     * @param requestJson Holds the appropriate info to correctly format the queried data.
     * @return The appropriate {@link JsonResponse} depending on the value of the Library in the {@link RequestInfo}.
     * @throws RequestBodyException
     */
    public JsonResponse handleRequest(RequestInfo requestJson) throws RequestBodyException{

        List<Result> dbAccessResults;

        dbAccessResults = new DBAccess().query(requestJson.getQueries());

        if (dbAccessResults == null)
            throw new RequestBodyException("DBAccess Error", HttpStatus.INTERNAL_SERVER_ERROR);

        if(requestJson.getChartType() == null)
            throw new RequestBodyException("Null chart type",HttpStatus.UNPROCESSABLE_ENTITY);

        try {
            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case Highcharts:

                    HighChartsJsonResponse retResponse;
                    try {
                         retResponse = new HighChartsDataFormatter().toJsonResponse(dbAccessResults,
                                SupportedChartTypes.valueOf(requestJson.getChartType()));

                    }catch (IllegalArgumentException e){
                        throw new RequestBodyException("Not supported chart type",HttpStatus.UNPROCESSABLE_ENTITY);
                    }

                    if(retResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    return retResponse;

                default:
                    throw new RequestBodyException(HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (IllegalArgumentException e){
            throw new RequestBodyException(HttpStatus.UNPROCESSABLE_ENTITY);
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
