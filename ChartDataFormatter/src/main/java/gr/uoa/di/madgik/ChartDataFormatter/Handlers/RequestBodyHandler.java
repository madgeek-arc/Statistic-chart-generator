package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.GoogleChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the Request Body and propagates the workload to the correct {@link DataFormatter}.
 */
@Service
public class RequestBodyHandler {

    private StatsService statsService;
    private final Logger log = Logger.getLogger(this.getClass());

    public RequestBodyHandler(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * @param requestJson Holds the appropriate info to correctly format the queried data.
     * @return The appropriate {@link JsonResponse} depending on the value of the Library in the {@link RequestInfo}.
     * @throws RequestBodyException
     */
    public JsonResponse handleRequest(RequestInfo requestJson) throws RequestBodyException {

        List<Result> statsServiceResults;

        try {
            statsServiceResults = this.statsService.query(requestJson.getChartQueries());

            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case HighCharts:

                    this.logChartInfo(requestJson, statsServiceResults);

                    HighChartsJsonResponse highChartsJsonResponse;
                    try {
                        highChartsJsonResponse = new HighChartsDataFormatter().toJsonResponse(statsServiceResults,
                                requestJson.getChartTypes(), requestJson.getChartNames());

                    }catch (DataFormatter.DataFormationException e){
                        throw new RequestBodyException(e.getMessage(),e,HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(highChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    highChartsJsonResponse.logJsonResponse();

                    return highChartsJsonResponse;

                case GoogleCharts:

                    this.logChartInfo(requestJson, statsServiceResults);

                    GoogleChartsJsonResponse googleChartsJsonResponse;
                    try{
                        googleChartsJsonResponse = new GoogleChartsDataFormatter().toJsonResponse(statsServiceResults,
                                requestJson.getChartTypes(), requestJson.getChartNames());

                    } catch (DataFormatter.DataFormationException e) {
                        throw new RequestBodyException(e.getMessage(),e, HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(googleChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    googleChartsJsonResponse.logJsonResponse();

                    return googleChartsJsonResponse;
                case HighMaps:

                    this.logChartInfo(requestJson, statsServiceResults);

                    GoogleChartsJsonResponse tempHighMapsJsonResponse;
                    try{
                        List<SupportedChartTypes> tempTypeList = new ArrayList<>();
                        for (String chartName : requestJson.getChartNames())
                            tempTypeList.add(SupportedChartTypes.area);

                        tempHighMapsJsonResponse = new GoogleChartsDataFormatter().toJsonResponse(statsServiceResults,
                                tempTypeList, requestJson.getChartNames());

                    } catch (DataFormatter.DataFormationException e) {
                        throw new RequestBodyException(e.getMessage(),e, HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(tempHighMapsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    tempHighMapsJsonResponse.logJsonResponse();

                    return tempHighMapsJsonResponse;


                default:
                    throw new RequestBodyException("Chart Library not supported yet",HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (RequestBodyException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestBodyException("Chart Data Formation Error:" + e.getMessage() , e, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void logChartInfo(RequestInfo requestJson, List<Result> statsServiceResults) {
        if(log.isInfoEnabled()) {
            log.info("Chart Types: " + requestJson.getChartTypes());
            log.info("Chart Names: " + requestJson.getChartNames());

            for (int i = 0; i < statsServiceResults.size(); i++) {
                Result res = statsServiceResults.get(i);
                log.info("Stats Service Results [" + i + "]: " + res.getRows().toString());
            }
        }
    }
}

