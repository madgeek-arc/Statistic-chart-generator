package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyException;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataRequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/raw")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST}, origins = "*")
public class RawDataFormatterRestController {

    private RequestBodyHandler requestBodyHandler;
    private final Logger log = LogManager.getLogger(this.getClass());

    public RawDataFormatterRestController(RequestBodyHandler requestBodyHandler) {
        this.requestBodyHandler = requestBodyHandler;
    }

    @GetMapping
    public @ResponseBody ResponseEntity<JsonResponse> getRawData(@RequestParam(name = "json") String json) throws IOException {
        RawDataRequestInfo requestInfo = new ObjectMapper().readValue(json, RawDataRequestInfo.class);

        JsonResponse responseData;

        try {
            responseData = requestBodyHandler.handleRawDataRequest(requestInfo);
        } catch (RequestBodyException e) {
            log.error("Error getting data", e);
            return new ResponseEntity<>(e.getHttpStatus());
        }

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }
}
