package gr.uoa.di.madgik.ChartDataizer.Handlers;

import gr.uoa.di.madgik.ChartDataizer.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.JsonResponse;
import gr.uoa.di.madgik.statstool.db.DBAccess;
import gr.uoa.di.madgik.statstool.db.Result;
import gr.uoa.di.madgik.statstool.query.Query;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.RequestInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RequestBodyHandler {

    public JsonResponse handleRequest(RequestInfo requestJson) throws RequestBodyException{

        try {
            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case Highcharts:

                    List<Result> dbAccessResults = null;

                    /*//Test call here
                    dbAccessResults = getChartData(requestJson.getQueries());
                    */
                    dbAccessResults = new DBAccess().query(requestJson.getQueries());

                    if (dbAccessResults == null) {
                        throw new RequestBodyException("DBAccess Error", HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                    if(requestJson.getChartType() == null)
                        throw new RequestBodyException("Null chart type",HttpStatus.UNPROCESSABLE_ENTITY);

                    HighChartsJsonResponse retResponse = new DataFormatter().toHighchartsJsonResponse(dbAccessResults, requestJson.getChartType());

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

    //TODO Mock call that provides Data based on the queries given
    private List<Result> getChartData(ArrayList<Query> queries) throws IOException {

        //This responseJson will be ultimately provided by DBAccess
        ObjectMapper mapper = new ObjectMapper();
        /*Multidata test static file*/
//        JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/multiChartdataizerToHtml.json"));
        /*Simple data test static file*/
//        JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/chartdataizerToHtml.json"));
        /*Empty data test static file*/
//        JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/emptyDataResponse.json"));

        Result result = mapper.readValue(new URL("http://localhost:8080/jsonFiles/result1.json"),Result.class);
//        Result result = mapper.readValue(new File("src/test/resources/result1.json"),Result.class);
        ArrayList<Result> retList = new ArrayList<>();
        retList.add(result);

        return retList;
    }

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
