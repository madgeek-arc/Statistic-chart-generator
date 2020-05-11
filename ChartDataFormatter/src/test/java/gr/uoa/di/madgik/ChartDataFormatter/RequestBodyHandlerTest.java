package gr.uoa.di.madgik.ChartDataFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyException;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@TestComponent
@ComponentScan(basePackages = "gr.uoa.di.madgik.DBAccess")
@EnableAutoConfiguration
public class RequestBodyHandlerTest {

    @Autowired
    private RequestBodyHandler handler;

    void  setHandler(RequestBodyHandler handler) {
        this.handler = handler;
    }

    @Test
    public void handleHighChartsRequestTest() throws IOException, RequestBodyException {

        ObjectMapper mapper = new ObjectMapper();
        RequestInfo requestInfo = mapper.readValue(new File("src/test/resources/highcharts/testFiles/multiTypeRequestInfo.json"), RequestInfo.class);

        JsonResponse response = handler.handleRequest(requestInfo);

        assert response != null;
        assert response instanceof HighChartsJsonResponse;

        mapper.configure(SerializationFeature.INDENT_OUTPUT,true);
        String jsonResponse = mapper.writeValueAsString(response);
        System.out.println("JsonResponse: "+jsonResponse);
    }

    @Test
    public void handleGoogleChartsRequestTest() throws IOException, RequestBodyException {

        ObjectMapper mapper = new ObjectMapper();
        RequestInfo requestInfo = mapper.readValue(new File("src/test/resources/highcharts/testFiles/multiTypeRequestInfo.json"), RequestInfo.class);

        JsonResponse response = handler.handleRequest(requestInfo);

        assert response != null;
        assert !(response instanceof GoogleChartsJsonResponse);

        mapper.configure(SerializationFeature.INDENT_OUTPUT,true);
        String jsonResponse = mapper.writeValueAsString(response);
        System.out.println("JsonResponse: "+jsonResponse);

    }
}
