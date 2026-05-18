package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataRequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RequestInfo;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.ChartInfo;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlQueryService;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles the Request Body and propagates the workload to the correct {@link DataFormatter}.
 */
@Service
public class RequestBodyHandler {

    private StatsService statsService;
    private final NlQueryService nlQueryService;
    private final Logger log = LogManager.getLogger(this.getClass());

    public RequestBodyHandler(StatsService statsService, NlQueryService nlQueryService) {
        this.statsService = statsService;
        this.nlQueryService = nlQueryService;
    }

    public JsonResponse handleRequest(RequestInfo requestJson) throws RequestBodyException {
        try {
            List<ChartInfo> charts = requestJson.getChartsInfo();
            List<Result> results = new ArrayList<>(Collections.nCopies(charts.size(), null));
            List<Query> dslQueries = new ArrayList<>();
            List<Integer> dslPositions = new ArrayList<>();

            for (int i = 0; i < charts.size(); i++) {
                Query q = charts.get(i).getQuery();
                if (q != null && q.isNlQuery()) {
                    nlQueryService.verifySignature(q.getProfile(), q.getNl(), q.getSig());
                    results.set(i, nlQueryService.execute(q.getProfile(), q.getNl()));
                } else {
                    dslQueries.add(q);
                    dslPositions.add(i);
                }
            }

            if (!dslQueries.isEmpty()) {
                List<Result> dslResults = statsService.query(dslQueries, requestJson.getOrderBy());
                for (int i = 0; i < dslPositions.size(); i++) {
                    results.set(dslPositions.get(i), dslResults.get(i));
                }
            }

            return format(requestJson, results);
        } catch (SecurityException e) {
            throw new RequestBodyException("Invalid NL query signature", e, HttpStatus.FORBIDDEN);
        } catch (StatsServiceException e) {
            throw new RequestBodyException("Chart Data Formation Error:" + e.getMessage(), e, HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (RequestBodyException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestBodyException("Chart Data Formation Error:" + e.getMessage(), e, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private JsonResponse format(RequestInfo requestJson, List<Result> statsServiceResults) throws RequestBodyException {
        try {
            JsonResponse jsonResponse = null;

            switch (SupportedLibraries.valueOf(requestJson.getLibrary())) {

                case HighCharts:

                    this.logChartInfo(requestJson, statsServiceResults);

                    HighChartsJsonResponse highChartsJsonResponse;
                    try {
                        highChartsJsonResponse = new HighChartsDataFormatter().toJsonResponse(statsServiceResults,
                                requestJson.getChartTypes(), requestJson.getChartNames(), requestJson.isDrilldown());

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

                    log.debug("handling eCharts request");
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

            log.debug("response: " + jsonResponse);

            return jsonResponse;
        } catch (RequestBodyException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestBodyException("Chart Data Formation Error:" + e.getMessage() , e, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public JsonResponse handleRawDataRequest(RawDataRequestInfo requestInfo) throws RequestBodyException {
        List<gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody.RawDataSeriesInfo> seriesList = requestInfo.getSeries();
        List<Result> results = new ArrayList<>(Collections.nCopies(seriesList.size(), null));
        List<Query> dslQueries = new ArrayList<>();
        List<Integer> dslPositions = new ArrayList<>();

        try {
            for (int i = 0; i < seriesList.size(); i++) {
                Query q = seriesList.get(i).getQuery();
                if (q != null && q.isNlQuery()) {
                    nlQueryService.verifySignature(q.getProfile(), q.getNl(), q.getSig());
                    results.set(i, nlQueryService.execute(q.getProfile(), q.getNl()));
                } else {
                    dslQueries.add(q);
                    dslPositions.add(i);
                }
            }

            if (!dslQueries.isEmpty()) {
                for (Query q : dslQueries) log.debug("Query:" + q.getName());
                List<Result> dslResults = statsService.query(dslQueries, requestInfo.getOrderBy());
                for (int i = 0; i < dslPositions.size(); i++)
                    results.set(dslPositions.get(i), dslResults.get(i));
            }

            this.logChartInfo(requestInfo, results);

            if (!requestInfo.isVerbose()) {
                RawDataJsonResponse response = new RawDataJsonResponse();
                response.setData(new ArrayList<>());
                for (Result r : results)
                    response.getData().add(r.getRows());
                return response;
            } else {
                VerboseRawDataResponse response = new VerboseRawDataResponse();
                response.setSeries(new ArrayList<>());
                for (int i = 0; i < results.size(); i++) {
                    Result result = results.get(i);
                    VerboseRawDataSeries series = new VerboseRawDataSeries();
                    VerboseRawDataSerie serie = new VerboseRawDataSerie();
                    serie.setQuery(seriesList.get(i).getQuery());
                    serie.setRows(new ArrayList<>());
                    for (List<?> row : result.getRows())
                        serie.getRows().add(new VerboseRawDataRow(row));
                    series.setSeries(serie);
                    response.getSeries().add(series);
                }
                return response;
            }
        } catch (SecurityException e) {
            throw new RequestBodyException("Invalid NL query signature", e, HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            throw new RequestBodyException("Chart Data Formation Error:" + e.getMessage(), e, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void logChartInfo(RequestInfo requestJson, List<Result> statsServiceResults) {
        if(log.isInfoEnabled()) {
            log.debug("Chart Types: " + requestJson.getChartTypes());
            log.debug("Chart Names: " + requestJson.getChartNames());

            for (int i = 0; i < statsServiceResults.size(); i++) {
                Result res = statsServiceResults.get(i);
                log.debug("Stats Service Results [" + i + "]: " + res.getRows().toString());
            }
        }
    }

    private void logChartInfo(RawDataRequestInfo requestJson, List<Result> statsServiceResults) {
        if(log.isInfoEnabled()) {

            for (int i = 0; i < statsServiceResults.size(); i++) {
                Result res = statsServiceResults.get(i);
                log.debug("Stats Service Results [" + i + "]: " + res.getRows().toString());
            }
        }
    }
}

