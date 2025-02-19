package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation.ArrayOfEChartDataObjects;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation.EChartsDataObject;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation.EChartsGraphData;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation.EChartsGraphLink;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.EChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.Utility.NumberUtils;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;

import static gr.uoa.di.madgik.ChartDataFormatter.Utility.NumberUtils.parseValue;


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

            for (List<?> row : result.getRows()) {

                if (row.size() == 3) {
                    // The value of the 2nd Group BY
                    String xValueB = valueToString(row.get(2));
                    if (!namesToDataSeries.containsKey(xValueB)) {
                        namesToDataSeries.put(xValueB, new HashMap<>());
                        namesToTypes.put(xValueB, chartsType.get(i));
                    }

                    XtoYMapping = namesToDataSeries.get(xValueB);
                }

                // Get the first groupBy of the result row
                String yValue = valueToString(row.get(0));
                String xValue = valueToString(row.get(1));

                if (XtoYMapping != null)
                    XtoYMapping.put(xValue, yValue);
                else
                    throw new DataFormationException("XtoYMapping HashMap is NULL");
            }
        }

        log.debug("DataSeries Names: " + namesToDataSeries.keySet().toString());
        log.debug("DataSeries Types: " + namesToTypes.values().toString());

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
                case treemap:
                    ArrayList<Number> yValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (XtoYMapping.containsKey(xValue)) {

                            String yValue = XtoYMapping.get(xValue);
                            yValuesArray.add(parseValue(yValue));

                        } else
                            yValuesArray.add(null);
                    }
                    dataSeries.add(new ArrayOfValues(yValuesArray));
                    // in eCharts a column chart is a bar chart and a bar chart is a bar chart with the categories on yAxis
                    if(chartType.name().equals("column")) {
                        dataSeriesTypes.add("bar");
                        log.debug("Added " + "bar");
                    } else {
                        dataSeriesTypes.add(chartType.name());
                        log.debug("Added " + chartType.name());
                    }
                    break;

                case pie:
                    ArrayList<EChartsDataObject> yObjectValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (XtoYMapping.containsKey(xValue)) {

                            String yValue = XtoYMapping.get(xValue);
                            yObjectValuesArray.add(new EChartsDataObject(xValue, parseValue(yValue)));

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
            return ECSingleGroupBy(result, chartType, chartName);
        
            //If there are results handle them by row size
        switch(result.getRows().get(0).size())
        {
            case 2:
                return ECSingleGroupBy(result, chartType, chartName);
            case 3:

            if(chartType == SupportedChartTypes.dependencywheel || chartType == SupportedChartTypes.sankey)
                    return ECGraph(result, true, chartType, chartName);

                return ECDoubleGroupBy(result, chartType);
            case 4:
            if(chartType == SupportedChartTypes.dependencywheel || chartType == SupportedChartTypes.sankey)
                    return ECGraph(result, false, chartType, chartName);
            default:
                throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());
        }
    }

    private EChartsJsonResponse ECSingleGroupBy(Result result, SupportedChartTypes chartType, String chartName){

        LinkedHashMap<String,Integer> xAxis_categories = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();

        switch (chartType) {
            case area:
            case bar:
            case column:
            case line:
            case treemap:
                ArrayList<Number> yValuesArray = new ArrayList<>();
                for (List<?> row : result.getRows()) {

                    String xValue = valueToString(row.get(1));

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    // I assume that always the first value of the row is for the Y value
                    String yValue = valueToString(row.get(0));
                    yValuesArray.add(parseValue(yValue));

                }
                dataSeries.add(new ArrayOfValues(yValuesArray));
                break;

            case pie:
                ArrayList<EChartsDataObject> yObjectValuesArray = new ArrayList<>();
                for (List<?> row : result.getRows()) {

                    String xValue = valueToString(row.get(1));

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    // I assume that always the first value of the row is for the Y value
                    String yValue = valueToString(row.get(0));
                    yObjectValuesArray.add(new EChartsDataObject(xValue, parseValue(yValue)));

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

    private EChartsJsonResponse ECDoubleGroupBy(Result result, SupportedChartTypes chartType){

        LinkedHashMap<String, Integer> xAxis_categories = new LinkedHashMap<>();
        LinkedHashMap<String, HashMap<String, String>> groupByMap = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        ArrayList<String> dataSeriesTypes = new ArrayList<>();

        for (List<?> row : result.getRows()) {

            // Create a map with the unique values for the group by
            String groupByValue = valueToString(row.get(2));
            String xValueA = valueToString(row.get(1));
            // I assume that always the first value of the row is for the Y value
            String yValue = valueToString(row.get(0));

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
            case treemap:

                for (HashMap<String, String> XValueToYValueMapping : groupByMap.values()) {

                    ArrayList<Number> yValuesArray = new ArrayList<>();
                    for (String xValue : xAxis_categories.keySet()) {

                        if (XValueToYValueMapping.containsKey(xValue)) {

                            String yValue = XValueToYValueMapping.get(xValue);
                            yValuesArray.add(parseValue(yValue));

                        }
                        else
                            yValuesArray.add(null);
                    }
                    dataSeries.add(new ArrayOfValues(yValuesArray));
                    // in eCharts a column chart is a bar chart and a bar chart is a bar chart with the categories on yAxis
                    if(chartType.name().equals("column")) {
                        dataSeriesTypes.add("bar");
                        log.debug("Added " + "bar");
                    } else {
                        dataSeriesTypes.add(chartType.name());
                        log.debug("Added " + chartType.name());
                    }
                }

                return new EChartsJsonResponse(dataSeries, new ArrayList<>(xAxis_categories.keySet()),
                        new ArrayList<>(groupByMap.keySet()), dataSeriesTypes);

            case pie:

                ArrayList<DataObject> mainSlicesValuesArray = new ArrayList<>();
                ArrayList<AbsData> drillDownArray = new ArrayList<>();

                for (String groupByX : groupByMap.keySet()) {

                    HashMap<String, String> XValueToYValueMapping = groupByMap.get(groupByX);

                    float pieSliceSum = 0;
                    ArrayList<EChartsDataObject> drillDownSliceValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_categories.keySet()) {

                        String yValue = XValueToYValueMapping.get(xValue);
                        Number value = parseValue(yValue);
                        drillDownSliceValuesArray.add(new EChartsDataObject(xValue, value));
                        if (value != null) {
                            pieSliceSum += value.floatValue();
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
                    log.debug("Added " + "bar");
                } else {
                    dataSeriesTypes.add(chartType.name());
                    log.debug("Added " + chartType.name());
                }

                EChartsJsonResponse ret = new EChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()),
                        null, dataSeriesTypes);
                ret.setDrilldown(drillDownArray);
                return ret;


            default:
                return null;
        }
    }

    private EChartsJsonResponse ECGraph(Result result, boolean ignoreNodeWeight, SupportedChartTypes chartType, String chartName){
        // For the purpose of making this as scalable as possible, we will consider the following assumption:
        // The query for the dependency wheel responds with the following rows :
        //  4 rows result: | from node | from node value | to node | from-to edge weight |
        //  3 rows result: | from node | to node | from-to edge weight |

        // ECharts Dependency Wheel and Sankey data are :
        // links : [{source, target , value(optional)}]
        // data : [{name, value}]
        
        List<EChartsGraphLink> links = new ArrayList<>();
        List<EChartsDataObject> data = new ArrayList<>();

        // At this point we know that the result has 3 or 4 rows
        if(ignoreNodeWeight)
        {   
            // The HashMap will help us find the fromNode value
            HashMap<String, Number> nodeToValueMap = new HashMap<>();
            for (List<?> row : result.getRows()) {
                
                // We assume the node and edge values are Integers

                String fromNode = valueToString(row.get(0));
                String toNode = valueToString(row.get(1));
                Number edgeWeight = NumberUtils.parseValue(row.get(2).toString());
                links.add(new EChartsGraphLink(fromNode, toNode, edgeWeight));

                // Add the edgeWeight in the HashMap with the fromNode as a key
                Number tempNodeValue = nodeToValueMap.putIfAbsent(fromNode, edgeWeight);
                if(tempNodeValue != null)
                    nodeToValueMap.put(fromNode, tempNodeValue.intValue() + edgeWeight.intValue());
            }

            // Convert the HashMap into the data list
            nodeToValueMap.forEach((fromNode, fromNodeValue) -> {
                data.add(new EChartsDataObject(fromNode, fromNodeValue));
            });

        }
        else
        {   
            // The HashMap will help us find the fromNode value
            HashMap<String, Number> nodeToValueMap = new HashMap<>();
            
            for (List<?> row : result.getRows()) {
                
                // We assume the node and edge values are Integers

                String fromNode = valueToString(row.get(0));
                Number fromNodeValue = NumberUtils.parseValue(String.valueOf(row.get(1)));
                String toNode = valueToString(row.get(2));
                Number edgeWeight = NumberUtils.parseValue(String.valueOf(row.get(3)));
                links.add(new EChartsGraphLink(fromNode, toNode, edgeWeight));
                
                // Add the edgeWeight in the HashMap with the fromNode as a key
                nodeToValueMap.putIfAbsent(fromNode, fromNodeValue);
            }
            // Convert the HashMap into the data list
            nodeToValueMap.forEach((fromNode, fromNodeValue) -> {
                data.add(new EChartsDataObject(fromNode, fromNodeValue));
            });
        }

        EChartsGraphData graph = new EChartsGraphData(links, data);

        // Fill the EChartsJsonResponse
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        dataSeries.add(graph);
        ArrayList<String> chartNames = new ArrayList<>();
        chartNames.add(chartName);
        ArrayList<String> chartTypes = new ArrayList<>();
        chartTypes.add(chartType.name());

        return new EChartsJsonResponse(dataSeries, null, chartNames, chartTypes);
    }
}
