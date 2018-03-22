package gr.uoa.di.madgik.ChartDataizer.Handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import gr.uoa.di.madgik.ChartDataizer.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.SupportedChartTypes;
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

                    List<Result> dbAccessResults;

                    //Test call here
//                    dbAccessResults = getMissMatchChartData(requestJson.getQueries());
//                    dbAccessResults = getChartData(requestJson.getQueries());
                    dbAccessResults = new DBAccess().query(requestJson.getQueries());

                    if (dbAccessResults == null)
                        throw new RequestBodyException("DBAccess Error", HttpStatus.INTERNAL_SERVER_ERROR);

                    if(requestJson.getChartType() == null)
                        throw new RequestBodyException("Null chart type",HttpStatus.UNPROCESSABLE_ENTITY);

                    HighChartsJsonResponse retResponse;
                    try {
                         retResponse = new DataFormatter().toHighchartsJsonResponse(dbAccessResults,
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

    //TODO Mock call that provides Data based on the queries given
    private List<Result> getChartData(ArrayList<Query> queries) {

        ObjectMapper mapper = new ObjectMapper();
        Result result = null;
        try {
            result = mapper.readValue(new URL("http://localhost:8080/jsonFiles/resultPublications1.json"),Result.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
//        Result result = mapper.readValue(new File("src/test/resources/result1.json"),Result.class);
        ArrayList<Result> retList = new ArrayList<>();
        for(Query q : queries)
            retList.add(result);

        return retList;
    }

    private List<Result> getMissMatchChartData(ArrayList<Query> queries) {

        ObjectMapper mapper = new ObjectMapper();
        Result result1 = null;
        Result result2 = null;
        try {
            result1 = mapper.readValue(new URL("http://localhost:8080/jsonFiles/resultPublications1.json"),Result.class);
            result2 = mapper.readValue(new URL("http://localhost:8080/jsonFiles/resultPublications2.json"),Result.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
//        Result result = mapper.readValue(new File("src/test/resources/result1.json"),Result.class);
        ArrayList<Result> retList = new ArrayList<>();
        retList.add(result1);
        retList.add(result2);

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
