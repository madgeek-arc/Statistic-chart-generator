package gr.uoa.di.madgik.ChartDataFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class RequestBodyHandlerTest {

    @Test
    public void handleHighChartsRequestTest() throws IOException, RequestBodyHandler.RequestBodyException {

        ObjectMapper mapper = new ObjectMapper();
        RequestInfo requestInfo = mapper.readValue(new File("src/test/resources/highcharts/testFiles/multiTypeRequestInfo.json"), RequestInfo.class);

        RequestBodyHandler handler = new RequestBodyHandler();
        JsonResponse response = handler.handleRequest(requestInfo);

        assert response != null;
        assert response instanceof HighChartsJsonResponse;

        mapper.configure(SerializationFeature.INDENT_OUTPUT,true);
        String jsonResponse = mapper.writeValueAsString(response);
        System.out.println("JsonResponse: "+jsonResponse);
    }

    @Test
    public void handleGoogleChartsRequestTest() throws IOException, RequestBodyHandler.RequestBodyException {

        ObjectMapper mapper = new ObjectMapper();
        RequestInfo requestInfo = mapper.readValue(new File("src/test/resources/highcharts/testFiles/multiTypeRequestInfo.json"), RequestInfo.class);

        RequestBodyHandler handler = new RequestBodyHandler();
        JsonResponse response = handler.handleRequest(requestInfo);

        assert response != null;
        assert !(response instanceof GoogleChartsJsonResponse);

        mapper.configure(SerializationFeature.INDENT_OUTPUT,true);
        String jsonResponse = mapper.writeValueAsString(response);
        System.out.println("JsonResponse: "+jsonResponse);

    }
}
