package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static gr.uoa.di.madgik.ChartDataFormatter.Utility.NumberUtils.parseValue;


/**
 * Extends DataFormatter handling the formation of data returned from DBAccess
 * to a format convenient for HighCharts library.
 *
 * @see DataFormatter
 */
public class HighChartsDataFormatter extends DataFormatter {

    private final Logger log = LogManager.getLogger(this.getClass());

    /**
     * {@inheritDoc}
     *
     * @return A {@link HighChartsJsonResponse} ready to be passed as a response body.
     */
    @Override
    @SuppressWarnings("unchecked")
    public HighChartsJsonResponse toJsonResponse(List<Result> dbAccessResults, Object... args) throws DataFormationException {

        /* ASSUMPTIONS:
         * ~ Results have a CONSISTENT [y,x] or a [y,x1,x2] format.
         * ~ Dates are returned as a String format
         * ~ Results and Chart Types match 1-1
         */

        if (args[0] == null || args[1] == null)
            throw new DataFormationException("No ChartType and ChartNames list given.");

        List<SupportedChartTypes> chartsType = (List<SupportedChartTypes>) args[0];
        List<String> chartNames = (List<String>) args[1];

        //read isDrilldown field
        boolean isDrilldown = (boolean) args[2];

        //pass isDrilldown to singleToHighChartsJsonResponse
        if (dbAccessResults.size() == 1 && chartsType.size() == 1)
            return singleToHighChartsJsonResponse(dbAccessResults.get(0), chartsType.get(0), chartNames.get(0), isDrilldown);

        if (dbAccessResults.size() != chartsType.size())
            throw new DataFormationException("Result list and Chart Type list are of different size.");

        // Handle multiple Query results. Each result applies to one chart.
        return multiToHighChartsJsonResponse(dbAccessResults, chartsType, chartNames);
    }

    private List<String> getXAxisCategories(List<Result> dbAccessResults) {

        return this.getXAxisCategories(dbAccessResults, false);
    }

    private HighChartsJsonResponse singleToHighChartsJsonResponse(Result result,
                                                                  SupportedChartTypes chartType,
                                                                  String chartName, boolean isDrilldown) throws DataFormationException {

        //There are no Results
        if (result.getRows().isEmpty())
            return HCSingleGroupBy(result, chartType, chartName);

        //If there are results handle them by row size
        switch (result.getRows().get(0).size()) {
            case 2:
                return HCSingleGroupBy(result, chartType, chartName);
            case 3:

                if (chartType == SupportedChartTypes.dependencywheel || chartType == SupportedChartTypes.sankey)
                    return HCGraph(result, false, chartType, chartName);

                //handling isDrilldown in DoubleGroupBy
                return HCDoubleGroupBy(result, chartType, isDrilldown);
            case 4:
                if (chartType == SupportedChartTypes.dependencywheel || chartType == SupportedChartTypes.sankey)
                    return HCGraph(result, true, chartType, chartName);
            default:
                throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());
        }

    }

    private HighChartsJsonResponse HCSingleGroupBy(Result result, SupportedChartTypes chartType, String chartName) {

        LinkedHashMap<String, Integer> xAxis_categories = new LinkedHashMap<>();
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
                ArrayList<DataObject> yObjectValuesArray = new ArrayList<>();
                for (List<?> row : result.getRows()) {

                    String xValue = valueToString(row.get(1));

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    // I assume that always the first value of the row is for the Y value
                    String yValue = valueToString(row.get(0));
                    yObjectValuesArray.add(new DataObject(xValue, parseValue(yValue)));
                }
                dataSeries.add(new ArrayOfDataObjects(yObjectValuesArray));
                break;

            default:
                return null;
        }

        ArrayList<String> chartNames = new ArrayList<>();
        chartNames.add(chartName);
        ArrayList<String> chartTypes = new ArrayList<>();
        chartTypes.add(chartType.name());

        return new HighChartsJsonResponse(dataSeries, new ArrayList<>(xAxis_categories.keySet()), chartNames, chartTypes);
    }

    private HighChartsJsonResponse HCDoubleGroupBy(Result result, SupportedChartTypes chartType, boolean isDrilldown) {

        System.out.println("Result: " + result);

        LinkedHashMap<String, Integer> xAxis_categories = new LinkedHashMap<>();
        LinkedHashMap<String, HashMap<String, String>> groupByMap = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        ArrayList<String> dataSeriesTypes = new ArrayList<>();

        for (List<?> row : result.getRows()) {
            String groupByValue;
            String xValueA;

            if (chartType.equals(SupportedChartTypes.pie)) {
                groupByValue = valueToString(row.get(1));
                xValueA = valueToString(row.get(2));
            } else {
                if (!isDrilldown) {
                    groupByValue = valueToString(row.get(2));
                    xValueA = valueToString(row.get(1));
                } else {
                    groupByValue = valueToString(row.get(1));
                    xValueA = valueToString(row.get(2));
                }
            }
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
            case line:
            case treemap:
                return HCDoubleGroupByNoDrilldown(chartType, xAxis_categories, groupByMap, dataSeries, dataSeriesTypes);
            case bar:
            case column:
                if (isDrilldown)
                    return HCDoubleGroupByDrilldown(chartType, xAxis_categories, groupByMap, dataSeries, dataSeriesTypes);
                else
                    return HCDoubleGroupByNoDrilldown(chartType, xAxis_categories, groupByMap, dataSeries, dataSeriesTypes);
            case pie:
                return HCDoubleGroupByDrilldown(chartType, xAxis_categories, groupByMap, dataSeries, dataSeriesTypes);
            default:
                return null;
        }
    }

    private HighChartsJsonResponse HCDoubleGroupByDrilldown(SupportedChartTypes chartType, LinkedHashMap<String, Integer> xAxis_categories, LinkedHashMap<String, HashMap<String, String>> groupByMap, ArrayList<AbsData> dataSeries, ArrayList<String> dataSeriesTypes) {
        ArrayList<DataObject> mainSlicesValuesArray = new ArrayList<>();
        ArrayList<AbsData> drillDownArray = new ArrayList<>();

        for (String groupByX : groupByMap.keySet()) {

            HashMap<String, String> XValueToYValueMapping = groupByMap.get(groupByX);

            float pieSliceSum = 0;
            ArrayList<DataObject> drillDownSliceValuesArray = new ArrayList<>();

            for (String xValue : xAxis_categories.keySet()) {

                String yValue = XValueToYValueMapping.get(xValue);
                Number value = parseValue(yValue);

                if (yValue != null)
                    drillDownSliceValuesArray.add(new DataObject(xValue, value));
                if (value != null) {
                    pieSliceSum += value.floatValue();
                }

            }
            drillDownArray.add(new ArrayOfDataObjects(drillDownSliceValuesArray));

            DataObject pieSlice = new DataObject(groupByX, pieSliceSum);
            pieSlice.setDrilldown(groupByX);

            mainSlicesValuesArray.add(pieSlice);
        }

        dataSeries.add(new ArrayOfDataObjects(mainSlicesValuesArray));
        dataSeriesTypes.add(chartType.name());
        log.debug("Added " + chartType.name());

        //HighChartsJsonResponse ret = new HighChartsJsonResponse(dataSeries, new ArrayList<>(xAxis_categories.keySet()), null, dataSeriesTypes);
        HighChartsJsonResponse ret = new HighChartsJsonResponse(dataSeries, null, null, dataSeriesTypes);
        ret.setDrilldown(drillDownArray);
        return ret;
    }

    private HighChartsJsonResponse HCDoubleGroupByNoDrilldown(SupportedChartTypes chartType, LinkedHashMap<String, Integer> xAxis_categories, LinkedHashMap<String, HashMap<String, String>> groupByMap, ArrayList<AbsData> dataSeries, ArrayList<String> dataSeriesTypes) {
        for (HashMap<String, String> XValueToYValueMapping : groupByMap.values()) {

            ArrayList<Number> yValuesArray = new ArrayList<>();
            for (String xValue : xAxis_categories.keySet()) {

                if (XValueToYValueMapping.containsKey(xValue)) {

                    String yValue = XValueToYValueMapping.get(xValue);

                    yValuesArray.add(parseValue(yValue));

                } else
                    yValuesArray.add(null);
            }
            dataSeries.add(new ArrayOfValues(yValuesArray));
            dataSeriesTypes.add(chartType.name());
        }

        return new HighChartsJsonResponse(dataSeries, new ArrayList<>(xAxis_categories.keySet()),
                new ArrayList<>(groupByMap.keySet()), dataSeriesTypes);
    }

    private HighChartsJsonResponse multiToHighChartsJsonResponse(List<Result> dbAccessResults, List<SupportedChartTypes> chartsType, List<String> chartNames) throws DataFormationException {

        // A sorted List with all the possible x values occurring from the Queries.
        List<String> xAxis_Categories = this.getXAxisCategories(dbAccessResults);

        HashMap<String, HashMap<String, String>> namesToDataSeries = new LinkedHashMap<>();
        HashMap<String, SupportedChartTypes> namesToTypes = new HashMap<>();

        for (int i = 0; i < dbAccessResults.size(); i++) {
            Result result = dbAccessResults.get(i);

            // Error if the results are not empty and break the [y,x] or a [y,x1,x2] format assumption
            if (!result.getRows().isEmpty() && (result.getRows().get(0).size() != 2 && result.getRows().get(0).size() != 3))
                throw new DataFormationException("Unexpected Result Row size of: " + result.getRows().get(0).size());

            HashMap<String, String> XtoYMapping = null;
            if (result.getRows().isEmpty() || result.getRows().get(0).size() == 2) {

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
                    dataSeriesTypes.add(chartType.name());
                    break;

                case pie:
                    ArrayList<DataObject> yObjectValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (XtoYMapping.containsKey(xValue)) {

                            String yValue = XtoYMapping.get(xValue);
                            yObjectValuesArray.add(new DataObject(xValue, parseValue(yValue)));

                        } else
                            yObjectValuesArray.add(new DataObject(xValue, null));
                    }
                    dataSeries.add(new ArrayOfDataObjects(yObjectValuesArray));
                    dataSeriesTypes.add(chartType.name());
                    break;

                default:
                    dataSeries.add(null);
                    dataSeriesTypes.add(null);
                    break;
            }
        }

        return new HighChartsJsonResponse(dataSeries, xAxis_Categories, dataSeriesNames, dataSeriesTypes);
    }

    /**
     * Highcharts Dependency Wheel and Sankey data are :
     * <p> | from node (string) | to node (string) | from-to edge weight (int) |
     * <p>In this method, we aim to create the above data representation into GraphData
     */
    private HighChartsJsonResponse HCGraph(Result result, boolean ignoreNodeWeight, SupportedChartTypes chartType, String chartName) {
        // For the purpose of making this as scalable as possible, we will consider the following assumption:
        // The named query for the dependency wheel responds with the following rows :
        //  4 rows result: | from node | from node value | to node | from-to edge weight |
        //  3 rows result: | from node | to node | from-to edge weight |

        // Initialize the keys array
        List<String> keys = Arrays.asList("from", "to", "weight");

        // Initialize the data array
        ArrayList<Object[]> data = new ArrayList<>(result.getRows().size());

        for (List<?> row : result.getRows()) {

            // Initialize each data row with exactly the size of the keys
            ArrayList<Object> dataRow = new ArrayList<>();

            dataRow.add(row.get(0));

            // Ignore the 'from' node weight
            if (ignoreNodeWeight) {
                dataRow.add(row.get(2));
                // We assume the node and edge values are Integers
                dataRow.add(Integer.parseInt(String.valueOf(row.get(3))));
            } else {
                dataRow.add(row.get(1));
                // We assume the node and edge values are Integers
                dataRow.add(Integer.parseInt(String.valueOf(row.get(2))));
            }
            // Push the row into the data list
            data.add(dataRow.stream().toArray());
        }

        // Create the graph
        GraphData graph = new GraphData(keys, data);

        // Fill the HighChartsJsonResponse
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        dataSeries.add(graph);
        ArrayList<String> chartNames = new ArrayList<>();
        chartNames.add(chartName);
        ArrayList<String> chartTypes = new ArrayList<>();
        chartTypes.add(chartType.name());

        return new HighChartsJsonResponse(dataSeries, null, chartNames, chartTypes);
    }
}
