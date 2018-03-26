package gr.uoa.di.madgik.ChartDataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.RestControllers.ChartDataizerRestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;

import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnableAutoConfiguration
@ContextConfiguration(classes={TestContext.class,ChartDataizerRestController.class})
public class ChartDataizerRestControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Before
    public void setUp(){
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    public void unknownLibPost() throws Exception {

        RequestInfo mockUnknownRequestInfo = new RequestInfo();
        mockUnknownRequestInfo.setLibrary("Unknown");
        mockUnknownRequestInfo.setQueries(new ArrayList<>());
        mockUnknownRequestInfo.setChartType("line");

        ObjectMapper mapper = new ObjectMapper();
        String jsonQueryInfo = mapper.writeValueAsString(mockUnknownRequestInfo);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/chart").content(jsonQueryInfo)
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity());
    }

    @Test
    public void PostResponse() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        Query query = mapper.readValue(new File("src/test/resources/public/jsonFiles/query_test.json"),Query.class);
        RequestInfo mockRequestInfo = new RequestInfo();
        mockRequestInfo.setLibrary("Highcharts");
        mockRequestInfo.setQueries(new ArrayList<>());
        mockRequestInfo.getQueries().add(query);
        mockRequestInfo.setChartType("line");

        ResultActions ra = this.mockMvc.perform(MockMvcRequestBuilders.post("/chart")
                .content(mapper.writeValueAsString(mockRequestInfo))
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(MockMvcResultMatchers.jsonPath("@.series[0].data").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("@.series[0].data").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("@.xAxis_categories").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("@.xAxis_categories").isNotEmpty());


        System.out.println("Response: "+ra.andReturn().getResponse().getContentAsString());

    }
}
