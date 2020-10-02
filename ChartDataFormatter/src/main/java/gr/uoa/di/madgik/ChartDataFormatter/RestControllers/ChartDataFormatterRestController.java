package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagramsService;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyException;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.ShortenUrlInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.simple.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;


/**
 * One of the two entry points to the Chart Data Formatter (the second being {@link TableDataFormatterRestController}.
 * This class is responsible of handling and fulfilling the requests made for the creation of charts.
 */
@RestController
@RequestMapping("/chart")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST} , origins = "*")
public class ChartDataFormatterRestController {

    private RequestBodyHandler requestBodyHandler;
    private SupportedDiagramsService supportedDiagramsService;

    private final Logger log = LogManager.getLogger(this.getClass());

    public ChartDataFormatterRestController(RequestBodyHandler requestBodyHandler, SupportedDiagramsService supportedDiagramsService) {
        this.requestBodyHandler = requestBodyHandler;
        this.supportedDiagramsService = supportedDiagramsService;
    }

    @GetMapping
    public ModelAndView content(Model model){

        return new ModelAndView("chart");
    }

    /**
     * The method that handles the POST request for the properly formatted data for a chart.
     *
     * @param requestJson The Request Body of the POST request.
     * @return A {@link ResponseEntity} with a {@link JsonResponse} as its body with the requested data properly formatted for the supported library.
     */
    @PostMapping(consumes = "application/json; charset=UTF-8",
                produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity<JsonResponse>
    postFullChartRepresentation(@RequestBody RequestInfo requestJson)  {

        JsonResponse responseData;

        log.info("requestJson: " + requestJson.toString());

        try {
            responseData = requestBodyHandler.handleRequest(requestJson);
        } catch (RequestBodyException e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(e.getHttpStatus());
        }

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

    private static String[] getNames(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toArray(String[]::new);
    }

    @GetMapping(path = "/libraries",
            produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity getSupportedLibraries(){

        String[] supportedLibraries = getNames(SupportedLibraries.class);
        return new ResponseEntity<>(supportedLibraries, HttpStatus.OK);
    }

    @GetMapping( path = "/types",
            produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity getSupportedChartTypes(){

        List<SupportedDiagramsService.SupportedChart> supportedTypes = this.supportedDiagramsService.getSupportedCharts();
        return new ResponseEntity<>(supportedTypes, HttpStatus.OK);
    }

    @GetMapping( path = "/maps",
            produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity getSupportedMaps(){

        List<SupportedDiagramsService.SupportedMap> supportedMaps = this.supportedDiagramsService.getSupportedMaps();
        return new ResponseEntity<>(supportedMaps, HttpStatus.OK);
    }

    @GetMapping( path = "/special",
            produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity getSupportedSpecialisedChartTypes(){

        List<SupportedDiagramsService.SupportedSpecialDiagram> supportedSpecialCharts = this.supportedDiagramsService.getSupportedSpecialDiagrams();
        return new ResponseEntity<>(supportedSpecialCharts, HttpStatus.OK);
    }

    @PostMapping( path = "/shorten",
                consumes = "application/json; charset=UTF-8",
                produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity<JSONObject> shortenChartUrl(@RequestBody ShortenUrlInfo request) {

        String url = request.getUrlToShorten();
        String getUrl = "https://tinyurl.com/api-create.php?url="+url;

        RestTemplate rt = new RestTemplate();
        String shortenedUrl = null;
        JSONObject response = new JSONObject();
        try {
            URI getUri = new URI(getUrl);
            ResponseEntity responseEntity =  rt.getForEntity(getUri,String.class);
            shortenedUrl = responseEntity.getBody().toString();
            log.info(shortenedUrl);

        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
            response.put("shortUrl", shortenedUrl);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        response.put("shortUrl", shortenedUrl);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping( path = "/json",
            produces = "application/json; charset=UTF-8")
    public @ResponseBody ResponseEntity<JsonResponse> json(@RequestParam(name="json") String json){

        JsonResponse responseData;
        ObjectMapper mapper = new ObjectMapper();



        try {
            RequestInfo requestJson = mapper.readValue(json, RequestInfo.class);

            responseData = requestBodyHandler.handleRequest(requestJson);
        } catch (RequestBodyException e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(e.getHttpStatus());
        } catch (IOException e) {
            log.error(e);
            return new ResponseEntity<JsonResponse>(HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

}
