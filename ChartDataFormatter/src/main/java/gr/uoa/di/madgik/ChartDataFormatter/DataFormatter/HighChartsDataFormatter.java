package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.springframework.lang.NonNull;

import java.util.*;

/**
 * Extends DataFormatter handling the formation of data returned from DBAccess
 * to a format convenient for HighCharts library.
 * @see DataFormatter
 */
public class HighChartsDataFormatter extends DataFormatter{

    //TODO Testing on extreme results

    /**
     * {@inheritDoc}
     *
     * @return A {@link HighChartsJsonResponse} ready to be passed as a response body.
     */
    @Override
    public HighChartsJsonResponse toJsonResponse(List<Result> dbAccessResults, List<SupportedChartTypes> chartsType) throws DataFormationException {

        /* ASSUMPTIONS:
         * ~ Results have a [y,x] or a [y,x1,x2] format.
         * ~ Dates are returned as a String format
         * ~ Results and Chart Types match 1-1
         */

        if (dbAccessResults.size() == 1 && chartsType.size() == 1)
            return singleToHighChartsJsonResponse(dbAccessResults.get(0), chartsType.get(0));

        if (dbAccessResults.size() != chartsType.size())
            throw new DataFormationException("Result list and Chart Type list are of different size.");

        AbstractMap.SimpleEntry<List<String>, List<HashMap<String, String>>> xAxis_CategoriesToXYMapping
                = assembleXAxisCategoriesToXYMapping(dbAccessResults);

        //A sorted List with all the possible x values occurring from the Queries.
        List<String> xAxis_Categories = xAxis_CategoriesToXYMapping.getKey();

        //A List which holds every Result in a HashMap, mapping y values to x values ([x,y]).
        List<HashMap<String, String>> allRowsXValueToYValueMappings = xAxis_CategoriesToXYMapping.getValue();

        ArrayList<AbsData> dataSeries = new ArrayList<>();
        ArrayList<String> dataSeriesTypes = new ArrayList<>();

        // Create the necessary AbsData object by mapping to all the possible xValues their corresponding yValues.
        // Fill with null if need be.
        for (int i = 0; i < allRowsXValueToYValueMappings.size(); i++){

            HashMap<String, String> xValuetoY = allRowsXValueToYValueMappings.get(i);
            SupportedChartTypes chartType = chartsType.get(i);

            switch (chartType) {
                case area:
                case bar:
                case column:
                case line:
                    ArrayList<Number> yValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (xValuetoY.containsKey(xValue)) {

                            String yValue = xValuetoY.get(xValue);
                            if(yValue == null)
                                yValuesArray.add(null);
                            else if (yValue.contains("."))
                                yValuesArray.add(Float.parseFloat(yValue));
                            else
                                yValuesArray.add(Integer.parseInt(yValue));
                        } else
                            yValuesArray.add(null);
                    }
                    dataSeries.add(new ArrayOfValues(yValuesArray));
                    dataSeriesTypes.add(chartType.name());
                    break;

                case pie:
                    ArrayList<DataObject> yObjectValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (xValuetoY.containsKey(xValue)) {

                            String yValue = xValuetoY.get(xValue);
                            if(yValue == null)
                                yObjectValuesArray.add(new DataObject(xValue , null));
                            else if (yValue.contains("."))
                                yObjectValuesArray.add(new DataObject(xValue , Float.parseFloat(yValue)));
                            else
                                yObjectValuesArray.add(new DataObject(xValue, Integer.parseInt(yValue)));
                        } else
                            yObjectValuesArray.add(new DataObject(xValue, null));
                    }
                    dataSeries.add(new ArrayOfObjects(yObjectValuesArray));
                    dataSeriesTypes.add(chartType.name());
                    break;

                default:
                    dataSeries.add(null);
                    dataSeriesTypes.add(null);
                    break;
            }
        }

        if(dataSeries.isEmpty() || xAxis_Categories.isEmpty())
            return null;

        return new HighChartsJsonResponse(dataSeries,xAxis_Categories, null, dataSeriesTypes);
    }

    private AbstractMap.SimpleEntry<List<String>,List<HashMap<String,String>>>
        assembleXAxisCategoriesToXYMapping(List<Result> dbAccessResults){

        //A HashSet with all the possible x values occurring from the Queries.
        LinkedHashSet<String> xAxis_categories = new LinkedHashSet<>();

        //A List which holds every Result in a HashMap, mapping y values to x values ([x,y]).
        ArrayList<HashMap<String,String>> allRowsXValueToYValueMappings = new ArrayList<>();

        for(Result result : dbAccessResults) {
            if (result.getRows().isEmpty())
                break;

            HashMap<String, String> rowXValueToYValueMapping = new HashMap<>();
            for(ArrayList<String> row : result.getRows()){

                // I assume that always the first value of the row is for the Y value
                String yValue = row.get(0);
                String xValue = row.get(1);

                //Finding a xValue and registering it in the xAxis_categories
                if (!xAxis_categories.contains(xValue))
                    xAxis_categories.add(xValue);

                //Filling the HashMap with the y value to x value map
                rowXValueToYValueMapping.put(xValue,yValue);
            }
            //Adding the finished HashMap to the List
            allRowsXValueToYValueMappings.add(rowXValueToYValueMapping);
        }

        ArrayList<String> sortedXAxis_Categories = new ArrayList<>(xAxis_categories);
        sortedXAxis_Categories.sort(String::compareToIgnoreCase);

        return new AbstractMap.SimpleEntry<>(sortedXAxis_Categories,allRowsXValueToYValueMappings);
    }



    private HighChartsJsonResponse singleToHighChartsJsonResponse(@NonNull Result result,@NonNull SupportedChartTypes chartType){

        //There are no Results
        if(result.getRows().isEmpty())
            return null;

        if(result.getRows().get(0).size() == 2)
            return singleHCSingleGroupBy(result, chartType);
        else if(result.getRows().get(0).size() == 3)
            return singleHCDoubleGroupBy(result, chartType);
        else
            return null;
    }

    private HighChartsJsonResponse singleHCSingleGroupBy(@NonNull Result result,@NonNull SupportedChartTypes chartType){

        LinkedHashMap<String,Integer> xAxis_categories = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();


        switch (chartType) {
            case area:
            case bar:
            case column:
            case line:
                ArrayList<Number> yValuesArray = new ArrayList<>();
                for (ArrayList<String> row : result.getRows()) {

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
                ArrayList<DataObject> yObjectValuesArray = new ArrayList<>();
                for (ArrayList<String> row : result.getRows()) {

                    String xValue = row.get(1);

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    // I assume that always the first value of the row is for the Y value
                    String yValue = row.get(0);
                    if(yValue == null)
                        yObjectValuesArray.add(new DataObject(xValue , null));
                    else if (yValue.contains("."))
                        yObjectValuesArray.add(new DataObject(xValue , Float.parseFloat(yValue)));
                    else
                        yObjectValuesArray.add(new DataObject(xValue ,Integer.parseInt(yValue)));
                }
                dataSeries.add(new ArrayOfObjects(yObjectValuesArray));
                break;

            default:
                return null;
        }

        if(dataSeries.isEmpty() || xAxis_categories.isEmpty()) {
            return null;
        }

        return new HighChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()));
    }

    private HighChartsJsonResponse singleHCDoubleGroupBy(@NonNull Result result,@NonNull SupportedChartTypes chartType){

        LinkedHashMap<String, Integer> xAxis_categories = new LinkedHashMap<>();
        LinkedHashMap<String, HashMap<String, String>> groupByMap = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        ArrayList<String> dataSeriesTypes = new ArrayList<>();

        HashMap<String, String> rowXValueToYValueMapping = null;
        for (ArrayList<String> row : result.getRows()) {

            // Create a map with the unique values for the group by
            String groupByValue = row.get(2);
            String xValueA = row.get(1);
            // I assume that always the first value of the row is for the Y value
            String yValue = row.get(0);

            if (!groupByMap.containsKey(groupByValue)) {

                rowXValueToYValueMapping = new HashMap<>();
                groupByMap.put(groupByValue, rowXValueToYValueMapping);
            }
            if(rowXValueToYValueMapping != null)
                rowXValueToYValueMapping.put(xValueA, yValue);

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
                    dataSeriesTypes.add(chartType.name());
                }

                if(dataSeries.isEmpty() || xAxis_categories.isEmpty())
                    return null;
                else
                    return new HighChartsJsonResponse(dataSeries,
                            new ArrayList<>(xAxis_categories.keySet()),
                            new ArrayList<>(groupByMap.keySet()), dataSeriesTypes);

            case pie:

                ArrayList<DataObject> mainSlicesValuesArray = new ArrayList<>();
                ArrayList<AbsData> drillDownArray = new ArrayList<>();

                for (String groupByX : groupByMap.keySet()) {

                    HashMap<String, String> XValueToYValueMapping = groupByMap.get(groupByX);

                    Float pieSliceSum = new Float(0);
                    ArrayList<DataObject> drillDownSliceValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_categories.keySet()) {

                        String yValue = XValueToYValueMapping.get(xValue);

                        if (yValue == null)
                            drillDownSliceValuesArray.add(new DataObject(xValue, null));
                        else if (yValue.contains(".")) {
                            Float value = Float.parseFloat(yValue);
                            drillDownSliceValuesArray.add(new DataObject(xValue, value));
                            pieSliceSum += value;
                        }
                        else {
                            Integer value = Integer.parseInt(yValue);
                            drillDownSliceValuesArray.add(new DataObject(xValue, value));
                            pieSliceSum += value;
                        }
                    }
                    drillDownArray.add(new ArrayOfObjects(drillDownSliceValuesArray));

                    DataObject pieSlice = new DataObject(groupByX, pieSliceSum);
                    pieSlice.setDrilldown(groupByX);

                    mainSlicesValuesArray.add(pieSlice);
                }

                dataSeries.add(new ArrayOfObjects(mainSlicesValuesArray));
                dataSeriesTypes.add(chartType.name());

                if(!dataSeries.isEmpty() && !drillDownArray.isEmpty()) {
                    HighChartsJsonResponse ret = new HighChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()));
                    ret.setDrilldown(drillDownArray);
                    return ret;
                }
                else
                    return null;

            default:
                return null;
        }
    }
}
