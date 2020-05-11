package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyException;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;


/**
 * One of the two entry points to the Chart Data Formatter (the second being {@link ChartDataFormatterRestController}.
 * This class is responsible of handling and fulfilling the requests made for the creation of tables.
 */
@RestController
@RequestMapping("/table")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST} , origins = "*")
public class TableDataFormatterRestController {

    private RequestBodyHandler requestBodyHandler;
    private final Logger log = Logger.getLogger(this.getClass());

    public TableDataFormatterRestController(RequestBodyHandler requestBodyHandler) {
        this.requestBodyHandler = requestBodyHandler;
    }

    @GetMapping
    public ModelAndView content(Model model) {

        return new ModelAndView("table");
    }

    /**
     * The method that handles the POST request for the properly formatted data for a table.
     *
     * @param requestJson The Request Body of the POST request.
     * @return A {@link ResponseEntity} with a {@link JsonResponse} as its body with the requested data properly formatted for the supported library.
     */
    @PostMapping(consumes = "application/json; charset=UTF-8",
                produces = "application/json; charset=UTF-8")
    public @ResponseBody
    ResponseEntity<JsonResponse>
    postFullChartRepresentation(@RequestBody RequestInfo requestJson)  {

        JsonResponse responseData;

        try {
            responseData = requestBodyHandler.handleRequest(requestJson);
        } catch (RequestBodyException e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(e.getHttpStatus());
        }

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }
}
