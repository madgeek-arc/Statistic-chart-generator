package Handlers;

import JsonChartRepresentation.HighChartsDataRepresentation.AbsData;
import JsonChartRepresentation.HighChartsDataRepresentation.DataSeries;
import JsonChartRepresentation.Query;
import JsonChartRepresentation.QueryInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

public class RequestBodyHandler {

    public DataSeries handleRequest(QueryInfo requestJson) throws RequestBodyException{

        try {
            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case Highcharts:

                    AbsData[] DBAccessResponseJson = null;

                    try {
                        //TODO Call to DBAccess here
                        DBAccessResponseJson = getChartData(requestJson.getQueries());

                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RequestBodyException("Unsuccessful Deserialisation: Unexpected JSON NodeType"
                                , HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                    if (DBAccessResponseJson == null) {
                        throw new RequestBodyException("Unsuccessful Deserialisation: No 'data' object found in JSON"
                                , HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                    return new DataSeries(new ArrayList<>(Arrays.asList(DBAccessResponseJson)));

                default:
                    throw new RequestBodyException(HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (IllegalArgumentException e){
            throw new RequestBodyException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    //TODO Mock call that provides Data based on the queries given
    private AbsData[] getChartData(ArrayList<Query> queries) throws IOException {

        //This responseJson will be ultimately provided by DBAccess
        ObjectMapper mapper = new ObjectMapper();
        /*Multidata test static file*/
        JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/multiChartdataizerToHtml.json"));
        /*Simple data test static file*/
//        JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/chartdataizerToHtml.json"));

        return mapper.treeToValue(jsonNode, AbsData[].class);
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
