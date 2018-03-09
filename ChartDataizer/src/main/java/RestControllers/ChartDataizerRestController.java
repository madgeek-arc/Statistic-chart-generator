package RestControllers;

import JsonChartRepresentation.HighChartsDataRepresentation.*;
import JsonChartRepresentation.QueryInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.constraints.NotNull;
import java.io.IOException;
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
    DataSeries postFullChartRepresentation(@NotNull @RequestBody QueryInfo requestJson) throws IOException {

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

                //This responseJson will be ultimately provided by DBAccess
                ObjectMapper mapper = new ObjectMapper();
                /*Multidata test static file*/
                //JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/multiChartdataizerToHtml.json"));
                /*Simple data test static file*/
                JsonNode jsonNode = mapper.readTree(new URL("http://localhost:8080/jsonFiles/chartdataizerToHtml.json"));

                AbsData[] DBAccessResponseJson = mapper.treeToValue(jsonNode, AbsData[].class);

                if (DBAccessResponseJson == null) return null;
                return new DataSeries(new ArrayList<>(Arrays.asList(DBAccessResponseJson)));

            default:
                return null;
        }
    }
}
