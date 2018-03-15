package RestControllers;

import Handlers.RequestBodyHandler;
import JsonChartRepresentation.HighChartsDataRepresentation.*;
import JsonChartRepresentation.QueryInfo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.constraints.NotNull;



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

        RequestBodyHandler requestBodyHandler = new RequestBodyHandler();
        DataSeries responseData = null;

        try {
            responseData = requestBodyHandler.handleRequest(requestJson);
        } catch (RequestBodyHandler.RequestBodyException e) {
            e.getStackTrace();
            return new ResponseEntity<>(e.getHttpStatus());
        }

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }


}
