package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;

import static gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes.*;

/**
 * Extends DataFormatter handling the formation of data returned from DBAccess
 * to a format convenient for GoogleCharts library.
 * @see DataFormatter
 */
public class GoogleChartsDataFormatter extends DataFormatter {

    private final Logger log = LogManager.getLogger(this.getClass());

    /**
     * {@inheritDoc}
     *
     * @return A {@link GoogleChartsJsonResponse} ready to be passed as a response body.
     */
    @Override
    @SuppressWarnings("unchecked")
    public GoogleChartsJsonResponse toJsonResponse(List<Result> dbAccessResults, Object... args) throws DataFormationException {

        /* ASSUMPTIONS:
         * ~ Results have a [x,y] format.
         * ~ Dates are returned as a String format
         */
        List<SupportedChartTypes> chartTypes = (List<SupportedChartTypes>) args[0];
        List<String> chartNames = (List<String>) args[1];

        if(dbAccessResults.size() == 1 && chartNames.size() == 1)
            return singleToGoogleChartsJsonResponse(dbAccessResults.get(0), chartNames.get(0), chartTypes.get(0));

        List<List<Object>> formattedDataTable = new ArrayList<>();

        //A sorted List with all the possible x values occurring from the Queries.
        List<String> xAxis_Categories = this.getXAxisCategories(dbAccessResults);

        HashMap<String, HashMap<String, String>> namesToDataSeries = new HashMap<>();
        HashMap<String, SupportedChartTypes> namesToTypes = new HashMap<>();


        for( int i=0; i < dbAccessResults.size(); i++ ){
            Result result = dbAccessResults.get(i);

            if (result.getRows().isEmpty())
                continue;

            if( result.getRows().get(0).size() != 2 && result.getRows().get(0).size() != 3)
                throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());


            HashMap<String, String> XtoYMapping = null;
            if(result.getRows().get(0).size() == 2) {

                XtoYMapping = new HashMap<>();
                String chartName = chartNames.get(i) == null ? "Series " + (i+1) : chartNames.get(i);
                namesToDataSeries.put(chartName, XtoYMapping);
                namesToTypes.put(chartName, chartTypes.get(i));
            }

            for (List<String> row : result.getRows()) {

                if(row.size() == 3){
                    // The value of the 2nd Group BY
                    String xValueB = row.get(2);
                    if (!namesToDataSeries.containsKey(xValueB)) {
                        namesToDataSeries.put(xValueB, new HashMap<>());
                        namesToTypes.put(xValueB, chartTypes.get(i));
                    }

                    XtoYMapping = namesToDataSeries.get(xValueB);
                }

                // Get the first groupBy of the result row
                String yValue = row.get(0);
                String xValue = row.get(1);

                if(XtoYMapping != null)
                    XtoYMapping.put(xValue, yValue);
                else
                    throw new DataFormationException("XtoYMapping HashMap is NULL");
            }
        }

        log.info("DataSeries Names: " + namesToDataSeries.keySet().toString());
        log.info("DataSeries Types: " + namesToTypes.values().toString());

        ArrayList<String> dataSeriesNames = new ArrayList<>(namesToDataSeries.keySet());
        ArrayList<String> dataSeriesTypes = new ArrayList<>();

        ArrayList<String> headerValuesArray = new ArrayList<>();
        headerValuesArray.add(null);
        headerValuesArray.addAll(dataSeriesNames);

        for (String xValue : xAxis_Categories) {

            ArrayList<Object> rowValuesArray = new ArrayList<>();
            rowValuesArray.add(xValue);

            for (String dataSeriesName: dataSeriesNames) {
                HashMap<String, String> XtoYMapping = namesToDataSeries.get(dataSeriesName);

                if(dataSeriesTypes.size() < dataSeriesNames.size()) {
                    SupportedChartTypes chartType = namesToTypes.get(dataSeriesName);

                    switch (chartType) {
                        case area:
                        case line:
                            dataSeriesTypes.add(chartType.name());
                            break;
                        case bar:
                        case column:
                            dataSeriesTypes.add("bars");
                            break;
                        case pie:
                        default:
                            dataSeriesTypes.add(null);
                            break;
                    }
                }

                if (XtoYMapping.containsKey(xValue)) {

                    String yValue = XtoYMapping.get(xValue);
                    if(yValue == null)
                        rowValuesArray.add(null);
                    else if (yValue.contains("."))
                        rowValuesArray.add(Float.parseFloat(yValue));
                    else
                        rowValuesArray.add(Integer.parseInt(yValue));
                } else
                    rowValuesArray.add(null);
            }
            formattedDataTable.add(rowValuesArray);
        }

        return new GoogleChartsJsonResponse(formattedDataTable, headerValuesArray, dataSeriesTypes);
    }

    private List<String> getXAxisCategories(List<Result> dbAccessResults) {

        return this.getXAxisCategories(dbAccessResults, false);
    }

    private GoogleChartsJsonResponse singleToGoogleChartsJsonResponse(Result result, String chartName, SupportedChartTypes chartType) throws DataFormationException {

        //There are no Results
        if(result.getRows().isEmpty())
            return singleGCSingleGroupBy(result, chartName);

        if(result.getRows().get(0).size() == 2)
            return singleGCSingleGroupBy(result, chartName);
        else if(result.getRows().get(0).size() == 3)
            return singleHCDoubleGroupBy(result, chartType);
        else
            throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());
    }

    private GoogleChartsJsonResponse singleGCSingleGroupBy(Result result, String chartName){
        List<List<Object>> formattedDataTable = new ArrayList<>();

        ArrayList<String> headerValuesArray = new ArrayList<>();
        headerValuesArray.add(null);
        headerValuesArray.add(chartName);

        for (List<String> row : result.getRows()) {
            ArrayList<Object> valuesArray = new ArrayList<>();

            String yValue = row.get(0);
            String xValue = row.get(1);

            valuesArray.add(xValue);
            if(yValue == null)
                valuesArray.add(null);
            else if( yValue.contains("."))
                valuesArray.add(Float.parseFloat(yValue));
            else
                valuesArray.add(Integer.parseInt(yValue));

            formattedDataTable.add(valuesArray);
        }

        return new GoogleChartsJsonResponse(formattedDataTable, headerValuesArray, null);
    }

    private GoogleChartsJsonResponse singleHCDoubleGroupBy(Result result, SupportedChartTypes chartType){
        LinkedHashMap<String, Integer> xAxis_categories = new LinkedHashMap<>();
        LinkedHashMap<String, HashMap<String, String>> groupByMap = new LinkedHashMap<>();

        for (List<String> row : result.getRows()) {

            // Create a map with the unique values for the group by
            String groupByValue = row.get(2);
            String xValueA = row.get(1);
            // I assume that always the first value of the row is for the Y value
            String yValue = row.get(0);

            if (!groupByMap.containsKey(groupByValue))
                groupByMap.put(groupByValue, new HashMap<>());

            groupByMap.get(groupByValue).put(xValueA, yValue);

            // Create a map with the unique values on the X axis
            if (!xAxis_categories.containsKey(xValueA))
                xAxis_categories.put(xValueA, xAxis_categories.size());
        }

        List<List<Object>> formattedDataTable = new ArrayList<>();
        ArrayList<String> headerValuesArray = new ArrayList<>();
        headerValuesArray.add(null);
        headerValuesArray.addAll(groupByMap.keySet());

        for (String xValue : xAxis_categories.keySet()) {
            ArrayList<Object> valuesArray = new ArrayList<>();
            valuesArray.add(xValue);

            for (HashMap<String, String> XValueToYValueMapping : groupByMap.values()) {

                if (XValueToYValueMapping.containsKey(xValue)) {

                    String yValue = XValueToYValueMapping.get(xValue);

                    if (yValue == null)
                        valuesArray.add(null);
                    else if (yValue.contains("."))
                        valuesArray.add(Float.parseFloat(yValue));
                    else
                        valuesArray.add(Integer.parseInt(yValue));
                }
                else
                    valuesArray.add(null);

            }
            formattedDataTable.add(valuesArray);
        }
        return new GoogleChartsJsonResponse(formattedDataTable, headerValuesArray, null);
    }
}
