package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation.ArrayOfEChartDataObjects;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation.EChartsDataObject;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.EChartsJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;


/**
 * Extends DataFormatter handling the formation of data returned from DBAccess
 * to a format convenient for HighCharts library.
 * @see DataFormatter
 */
public class EChartsDataFormatter extends DataFormatter{

    private final Logger log = LogManager.getLogger(this.getClass());

    /**
     * {@inheritDoc}
     *
     * @return A {@link EChartsJsonResponse} ready to be passed as a response body.
     */
    @Override
    @SuppressWarnings("unchecked")
    public EChartsJsonResponse toJsonResponse(List<Result> dbAccessResults, Object... args) throws DataFormationException {

        /* ASSUMPTIONS:
         * ~ Results have a CONSISTENT [y,x] or a [y,x1,x2] format.
         * ~ Dates are returned as a String format
         * ~ Results and Chart Types match 1-1
         */

        if (args[0] == null || args[1] == null)
            throw new DataFormationException("No ChartType and ChartNames list given.");

        List<SupportedChartTypes> chartsType = (List<SupportedChartTypes>) args[0];
        List<String> chartNames = (List<String>) args[1];

        if (dbAccessResults.size() == 1 && chartsType.size() == 1)
            return singleToEChartsJsonResponse(dbAccessResults.get(0), chartsType.get(0), chartNames.get(0));

        if (dbAccessResults.size() != chartsType.size())
            throw new DataFormationException("Result list and Chart Type list are of different size.");

        //A sorted List with all the possible x values occurring from the Queries.
        List<String> xAxis_Categories = this.getXAxisCategories(dbAccessResults);

        HashMap<String, HashMap<String, String>> namesToDataSeries = new LinkedHashMap<>();
        HashMap<String, SupportedChartTypes> namesToTypes = new HashMap<>();

        for (int i = 0; i < dbAccessResults.size(); i++) {
            Result result = dbAccessResults.get(i);

            if (result.getRows().isEmpty())
                continue;


            if (result.getRows().get(0).size() != 2 && result.getRows().get(0).size() != 3)
                throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());


            HashMap<String, String> XtoYMapping = null;
            if (result.getRows().get(0).size() == 2) {

                XtoYMapping = new HashMap<>();
                String chartName = chartNames.get(i) == null ? "Series " + (i + 1) : chartNames.get(i);
                namesToDataSeries.put(chartName, XtoYMapping);
                namesToTypes.put(chartName, chartsType.get(i));
            }

            for (List<String> row : result.getRows()) {

                if (row.size() == 3) {
                    // The value of the 2nd Group BY
                    String xValueB = row.get(2);
                    if (!namesToDataSeries.containsKey(xValueB)) {
                        namesToDataSeries.put(xValueB, new HashMap<>());
                        namesToTypes.put(xValueB, chartsType.get(i));
                    }

                    XtoYMapping = namesToDataSeries.get(xValueB);
                }

                // Get the first groupBy of the result row
                String yValue = row.get(0);
                String xValue = row.get(1);

                if (XtoYMapping != null)
                    XtoYMapping.put(xValue, yValue);
                else
                    throw new DataFormationException("XtoYMapping HashMap is NULL");
            }
        }

        log.info("DataSeries Names: " + namesToDataSeries.keySet().toString());
        log.info("DataSeries Types: " + namesToTypes.values().toString());

        ArrayList<AbsData> dataSeries = new ArrayList<>();
        ArrayList<String> dataSeriesTypes = new ArrayList<>();
        ArrayList<String> dataSeriesNames = new ArrayList<>(namesToDataSeries.keySet());

        for (String dataSeriesName : dataSeriesNames) {

            HashMap<String, String> XtoYMapping = namesToDataSeries.get(dataSeriesName);
            SupportedChartTypes chartType = namesToTypes.get(dataSeriesName);

            switch (chartType) {
                case area:
                case bar:
                case column:
                case line:
                    ArrayList<Number> yValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (XtoYMapping.containsKey(xValue)) {

                            String yValue = XtoYMapping.get(xValue);
                            if (yValue == null)
                                yValuesArray.add(null);
                            else if (yValue.contains("."))
                                yValuesArray.add(Float.parseFloat(yValue));
                            else
                                yValuesArray.add(Integer.parseInt(yValue));
                        } else
                            yValuesArray.add(null);
                    }
                    dataSeries.add(new ArrayOfValues(yValuesArray));
                    // in eCharts a column chart is a bar chart and a bar chart is a bar chart with the categories on yAxis
                    if(chartType.name().equals("column")) {
                        dataSeriesTypes.add("bar");
                        log.info("Added " + "bar");
                    } else {
                        dataSeriesTypes.add(chartType.name());
                        log.info("Added " + chartType.name());
                    }
                    break;

                case pie:
                    ArrayList<EChartsDataObject> yObjectValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (XtoYMapping.containsKey(xValue)) {

                            String yValue = XtoYMapping.get(xValue);
                            if (yValue == null)
                                yObjectValuesArray.add(new EChartsDataObject(xValue, null));
                            else if (yValue.contains("."))
                                yObjectValuesArray.add(new EChartsDataObject(xValue, Float.parseFloat(yValue)));
                            else
                                yObjectValuesArray.add(new EChartsDataObject(xValue, Integer.parseInt(yValue)));
                        } else
                            yObjectValuesArray.add(new EChartsDataObject(xValue, null));
                    }
                    dataSeries.add(new ArrayOfEChartDataObjects(yObjectValuesArray));
                    dataSeriesTypes.add(chartType.name());
                    break;

                default:
                    dataSeries.add(null);
                    dataSeriesTypes.add(null);
                    break;
            }
        }

        return new EChartsJsonResponse(dataSeries,xAxis_Categories, dataSeriesNames, dataSeriesTypes);
    }

    private List<String> getXAxisCategories(List<Result> dbAccessResults) {

        return this.getXAxisCategories(dbAccessResults, false);
    }

    private EChartsJsonResponse singleToEChartsJsonResponse(Result result,
                                                            SupportedChartTypes chartType,
                                                            String chartName) throws DataFormationException {

        //There are no Results
        if(result.getRows().isEmpty())
            return singleECSingleGroupBy(result, chartType, chartName);

        if(result.getRows().get(0).size() == 2)
            return singleECSingleGroupBy(result, chartType, chartName);
        else if(result.getRows().get(0).size() == 3)
            return singleECDoubleGroupBy(result, chartType);
        else
            throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());
    }

    private EChartsJsonResponse singleECSingleGroupBy(Result result, SupportedChartTypes chartType, String chartName){

        LinkedHashMap<String,Integer> xAxis_categories = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();

        switch (chartType) {
            case area:
            case bar:
            case column:
            case line:
                ArrayList<Number> yValuesArray = new ArrayList<>();
                for (List<String> row : result.getRows()) {

                    String xValue = row.get(1);

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    // I assume that always the first value of the row is for the Y value
                    String yValue = row.get(0);
                    if(yValue == null)
                        yValuesArray.add(null);
                    else if (yValue.contains("."))
                        yValuesArray.add(Float.parseFloat(yValue));
                    else
                        yValuesArray.add(Integer.parseInt(yValue));
                }
                dataSeries.add(new ArrayOfValues(yValuesArray));
                break;

            case pie:
                ArrayList<EChartsDataObject> yObjectValuesArray = new ArrayList<>();
                for (List<String> row : result.getRows()) {

                    String xValue = row.get(1);

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    // I assume that always the first value of the row is for the Y value
                    String yValue = row.get(0);
                    if(yValue == null)
                        yObjectValuesArray.add(new EChartsDataObject(xValue , null));
                    else if (yValue.contains("."))
                        yObjectValuesArray.add(new EChartsDataObject(xValue , Float.parseFloat(yValue)));
                    else
                        yObjectValuesArray.add(new EChartsDataObject(xValue ,Integer.parseInt(yValue)));
                }
                dataSeries.add(new ArrayOfEChartDataObjects(yObjectValuesArray));
                break;

            default:
                return null;
        }

        ArrayList<String> chartNames = new ArrayList<>();
        chartNames.add(chartName);
        ArrayList<String> chartTypes = new ArrayList<>();
        // in eCharts a column chart is a bar chart and a bar chart is a bar chart with the categories on yAxis
        if(chartType.name().equals("column"))
            chartTypes.add("bar");
        else
            chartTypes.add(chartType.name());

        return new EChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()), chartNames, chartTypes);
    }

    private EChartsJsonResponse singleECDoubleGroupBy(Result result, SupportedChartTypes chartType){

        LinkedHashMap<String, Integer> xAxis_categories = new LinkedHashMap<>();
        LinkedHashMap<String, HashMap<String, String>> groupByMap = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        ArrayList<String> dataSeriesTypes = new ArrayList<>();

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

        switch (chartType) {
            case area:
            case bar:
            case column:
            case line:

                for (HashMap<String, String> XValueToYValueMapping : groupByMap.values()) {

                    ArrayList<Number> yValuesArray = new ArrayList<>();
                    for (String xValue : xAxis_categories.keySet()) {

                        if (XValueToYValueMapping.containsKey(xValue)) {

                            String yValue = XValueToYValueMapping.get(xValue);

                            if (yValue == null)
                                yValuesArray.add(null);
                            else if (yValue.contains("."))
                                yValuesArray.add(Float.parseFloat(yValue));
                            else
                                yValuesArray.add(Integer.parseInt(yValue));
                        }
                        else
                            yValuesArray.add(null);
                    }
                    dataSeries.add(new ArrayOfValues(yValuesArray));
                    // in eCharts a column chart is a bar chart and a bar chart is a bar chart with the categories on yAxis
                    if(chartType.name().equals("column")) {
                        dataSeriesTypes.add("bar");
                        log.info("Added " + "bar");
                    } else {
                        dataSeriesTypes.add(chartType.name());
                        log.info("Added " + chartType.name());
                    }
                }

                return new EChartsJsonResponse(dataSeries, new ArrayList<>(xAxis_categories.keySet()),
                        new ArrayList<>(groupByMap.keySet()), dataSeriesTypes);

            case pie:

                ArrayList<DataObject> mainSlicesValuesArray = new ArrayList<>();
                ArrayList<AbsData> drillDownArray = new ArrayList<>();

                for (String groupByX : groupByMap.keySet()) {

                    HashMap<String, String> XValueToYValueMapping = groupByMap.get(groupByX);

                    Float pieSliceSum = new Float(0);
                    ArrayList<EChartsDataObject> drillDownSliceValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_categories.keySet()) {

                        String yValue = XValueToYValueMapping.get(xValue);

                        if (yValue == null)
                            drillDownSliceValuesArray.add(new EChartsDataObject(xValue, null));
                        else if (yValue.contains(".")) {
                            Float value = Float.parseFloat(yValue);
                            drillDownSliceValuesArray.add(new EChartsDataObject(xValue, value));
                            pieSliceSum += value;
                        }
                        else {
                            Integer value = Integer.parseInt(yValue);
                            drillDownSliceValuesArray.add(new EChartsDataObject(xValue, value));
                            pieSliceSum += value;
                        }
                    }
                    drillDownArray.add(new ArrayOfEChartDataObjects(drillDownSliceValuesArray));

                    DataObject pieSlice = new DataObject(groupByX, pieSliceSum);
                    pieSlice.setDrilldown(groupByX);

                    mainSlicesValuesArray.add(pieSlice);
                }

                dataSeries.add(new ArrayOfDataObjects(mainSlicesValuesArray));

                // in eCharts a column chart is a bar chart and a bar chart is a bar chart with the categories on yAxis
                if(chartType.name().equals("column")) {
                    dataSeriesTypes.add("bar");
                    log.info("Added " + "bar");
                } else {
                    dataSeriesTypes.add(chartType.name());
                    log.info("Added " + chartType.name());
                }

                EChartsJsonResponse ret = new EChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()),
                        null, dataSeriesTypes);
                ret.setDrilldown(drillDownArray);
                return ret;


            default:
                return null;
        }
    }
}
