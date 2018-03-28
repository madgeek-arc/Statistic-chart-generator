package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.constraints.NotNull;


/**
 * The entry point to the Chart Data Formatter. This class is responsible of handling and fulfilling the requests made.
 */
@RestController
public class ChartDataFormatterRestController {

    @GetMapping("/chart")
    public ModelAndView content(Model model){

        return new ModelAndView("chart");
    }

    /**
     * The method that handles the POST request for the properly formatted data for a chart.
     *
     * @param requestJson The Request Body of the POST request.
     * @return A {@link ResponseEntity} with a {@link JsonResponse} as its body with the requested data properly formatted for the supported library.
     */
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
