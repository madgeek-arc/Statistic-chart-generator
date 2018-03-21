package gr.uoa.di.madgik.ChartDataizer.RestControllers;

import gr.uoa.di.madgik.ChartDataizer.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.JsonResponse;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.RequestInfo;

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
    ResponseEntity<JsonResponse> postFullChartRepresentation(@NotNull @RequestBody RequestInfo requestJson)  {

        RequestBodyHandler requestBodyHandler = new RequestBodyHandler();
        JsonResponse responseData;

        try {
            responseData = requestBodyHandler.handleRequest(requestJson);
        } catch (RequestBodyHandler.RequestBodyException e) {
            System.err.println(e.getMessage());
            e.getStackTrace();
            return new ResponseEntity<>(e.getHttpStatus());
        }

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }


}
