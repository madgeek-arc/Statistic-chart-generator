package RestControllers;

import JsonChartRepresentation.HighChartsDataRepresentation.*;
import JsonChartRepresentation.QueryInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;


enum SupportedLibraries {
    Highcharts;
}

@RestController
public class ChartDataizerRestController {

    @GetMapping("/chart")
    public ModelAndView content(Model model){

        return new ModelAndView("chart");
    }

    @PostMapping(path = "/chart",
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")

    public @ResponseBody
    ResponseEntity<DataSeries> postFullChartRepresentation(@NotNull @RequestBody QueryInfo requestJson)  {
        try{
            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {
                case Highcharts:

                    /*TODO Call to DBAccess here
                    {
                        Data responseData = DBAccess.getChartData(requestJson);

                    }*/
                    //Simulate query time from DBAccess
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    AbsData[] DBAccessResponseJson = null;

                    try {
                        //This responseJson will be ultimately provided by DBAccess
                        ObjectMapper mapper = new ObjectMapper();
                        /*Multidata test static file*/
                        //JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/multiChartdataizerToHtml.json"));
                        /*Simple data test static file*/
                        JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/chartdataizerToHtml.json"));
                        DBAccessResponseJson = mapper.treeToValue(jsonNode, AbsData[].class);

                    } catch (IOException e) {
                        e.printStackTrace();
                        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                    if (DBAccessResponseJson == null) return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

                    DataSeries dataSeries = new DataSeries(new ArrayList<>(Arrays.asList(DBAccessResponseJson)));
                    return new ResponseEntity<>(dataSeries, HttpStatus.OK);

                default:
                    return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
            }

        }catch(IllegalArgumentException e){
                return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
