package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataRequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
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
            statsServiceResults = this.statsService.query(requestJson.getChartQueries(), requestJson.getOrderBy());
            JsonResponse jsonResponse = null;

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

                    jsonResponse = highChartsJsonResponse;
                    break;
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

                    jsonResponse = googleChartsJsonResponse;
                    break;
                case eCharts:

                    log.info("handling eCharts request");
                    this.logChartInfo(requestJson, statsServiceResults);

                    EChartsJsonResponse eChartsJsonResponse;
                    try {
                        eChartsJsonResponse = new EChartsDataFormatter().toJsonResponse(statsServiceResults,
                                requestJson.getChartTypes(), requestJson.getChartNames());

                    }catch (DataFormatter.DataFormationException e){
                        throw new RequestBodyException(e.getMessage(),e,HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    if(eChartsJsonResponse == null)
                        throw new RequestBodyException("Error on data formation",HttpStatus.UNPROCESSABLE_ENTITY);

                    jsonResponse = eChartsJsonResponse;
                    break;
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

                    jsonResponse = tempHighMapsJsonResponse;
                    break;
                default:
                    throw new RequestBodyException("Chart Library not supported yet",HttpStatus.UNPROCESSABLE_ENTITY);
            }

            log.info("response: " + jsonResponse);

            return jsonResponse;
        } catch (RequestBodyException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestBodyException("Chart Data Formation Error:" + e.getMessage() , e, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public JsonResponse handleRawDataRequest(RawDataRequestInfo requestInfo) throws RequestBodyException {

        List<Result> statsServiceResults;

        for (Query q:requestInfo.getQueries())
            log.info("Query:" + q.getName() );

        try {
            statsServiceResults = this.statsService.query(requestInfo.getQueries());

            this.logChartInfo(requestInfo, statsServiceResults);

            if (!requestInfo.isVerbose()) {
                RawDataJsonResponse response = new RawDataJsonResponse();
                response.setData(new ArrayList<>());

                for (Result r : statsServiceResults)
                    response.getData().add(r.getRows());

                return response;
            } else {

                VerboseRawDataResponse response = new VerboseRawDataResponse();
                response.setSeries(new ArrayList<>());

                //for (Result r:statsServiceResults) {
                for (int i = 0; i < statsServiceResults.size(); i++) {
                    Result result = statsServiceResults.get(i);
                    VerboseRawDataSeries series = new VerboseRawDataSeries();
                    VerboseRawDataSerie serie = new VerboseRawDataSerie();

                    serie.setQuery(requestInfo.getQueries().get(i));
                    serie.setRows(new ArrayList<>());

                    for (List<String> row:result.getRows()) {
                        serie.getRows().add(new VerboseRawDataRow(row));
                    }

                    series.setSeries(serie);

                    response.getSeries().add(series);
                }

                return response;
            }

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

    private void logChartInfo(RawDataRequestInfo requestJson, List<Result> statsServiceResults) {
        if(log.isInfoEnabled()) {

            for (int i = 0; i < statsServiceResults.size(); i++) {
                Result res = statsServiceResults.get(i);
                log.info("Stats Service Results [" + i + "]: " + res.getRows().toString());
            }
        }
    }
}

