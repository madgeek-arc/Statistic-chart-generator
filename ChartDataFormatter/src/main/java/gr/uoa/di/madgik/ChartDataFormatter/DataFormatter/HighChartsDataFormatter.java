package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.statstool.db.Result;
import org.springframework.lang.NonNull;

import java.util.*;

/**
 * Extends DataFormatter handling the formation of data returned from DBAccess
 * to valid HighCharts library format.
 * @see DataFormatter
 */
public class HighChartsDataFormatter extends DataFormatter{

    //TODO Testing on extreme results

    /**
     * @param dbAccessResults A List of {@link Result} originating from DBAccess.
     * @param chartType The type of Chart that the List of Results will be formatted into.
     *                  This type of Chart should be supported across every Chart Library.
     * @return A {@link HighChartsJsonResponse} ready to be passed as a response body.
     */
    @Override
    public HighChartsJsonResponse toJsonResponse(List<Result> dbAccessResults, SupportedChartTypes chartType) {

        /* ASSUMPTIONS:
         * ~ Results have a [x,y] format.
         * ~ Dates are returned as a String format
         */

        if(dbAccessResults.size() == 1)
            return singleToHighchartsJsonResponse(dbAccessResults.get(0), chartType);

        AbstractMap.SimpleEntry<List<String>,List<HashMap<String,String>>> xAxis_CategoriesToXYMapping
                = assembleXAxisCategoriesToXYMapping(dbAccessResults);

        //A sorted List with all the possible x values occurring from the Queries.
        List<String> xAxis_Categories = xAxis_CategoriesToXYMapping.getKey();

        //A List which holds every Result in a HashMap, mapping y values to x values ([x,y]).
        List<HashMap<String,String>> allRowsXValueToYValueMappings = xAxis_CategoriesToXYMapping.getValue();

        ArrayList<AbsData> dataSeries = new ArrayList<>();

        // Create the necessary AbsData object by mapping to all the possible xValues their corresponding yValues.
        // Fill with null if need be.
        for (HashMap<String, String> xValuetoY : allRowsXValueToYValueMappings)
            switch (chartType) {
                case area:
                case bar:
                case column:
                case line:
                    ArrayList<Number> yValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (xValuetoY.containsKey(xValue)) {

                            String yValue = xValuetoY.get(xValue);
                            if (yValue.contains("."))
                                yValuesArray.add(Float.parseFloat(yValue));
                            else
                                yValuesArray.add(Integer.parseInt(yValue));
                        } else
                            yValuesArray.add(null);
                    }
                    dataSeries.add(new ArrayOfValues(yValuesArray));
                    break;

                case pie:
                    ArrayList<DataObject> yObjectValuesArray = new ArrayList<>();

                    for (String xValue : xAxis_Categories) {
                        if (xValuetoY.containsKey(xValue)) {

                            String yValue = xValuetoY.get(xValue);
                            if (yValue.contains("."))
                                yObjectValuesArray.add(new DataObject(xValue, Float.parseFloat(yValue)));
                            else
                                yObjectValuesArray.add(new DataObject(xValue, Integer.parseInt(yValue)));
                        } else
                            yObjectValuesArray.add(new DataObject(xValue, null));
                    }
                    dataSeries.add(new ArrayOfObjects(yObjectValuesArray));
                    break;

                default:
                    dataSeries.add(null);
                    break;
            }


        if(dataSeries.isEmpty() || xAxis_Categories.isEmpty())
            return null;

        return new HighChartsJsonResponse(dataSeries,xAxis_Categories);
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

            for (int i = 0; i<result.getRows().size(); i++){
                ArrayList<String> row = result.getRows().get(i);

                String xValue = row.get(0);
                String yValue = row.get(1);

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

    private HighChartsJsonResponse singleToHighchartsJsonResponse(@NonNull Result result,@NonNull SupportedChartTypes chartType){

        LinkedHashMap<String,Integer> xAxis_categories = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();

        if(result.getRows().isEmpty())
            return null;

        switch (chartType) {
            case area:
            case bar:
            case column:
            case line:
                ArrayList<Number> yValuesArray = new ArrayList<>();
                for (ArrayList<String> row : result.getRows()) {

                    String xValue = row.get(0);

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    String yValue = row.get(1);
                    if (yValue.contains("."))
                        yValuesArray.add(Float.parseFloat(yValue));
                    else
                        yValuesArray.add(Integer.parseInt(yValue));
                }
                dataSeries.add(new ArrayOfValues(yValuesArray));
                break;

            case pie:
                ArrayList<DataObject> yObjectValuesArray = new ArrayList<>();
                for (ArrayList<String> row : result.getRows()) {

                    String xValue = row.get(0);

                    if (!xAxis_categories.containsKey(xValue))
                        xAxis_categories.put(xValue, xAxis_categories.size());

                    String yValue = row.get(1);
                    if (yValue.contains("."))
                        yObjectValuesArray.add(new DataObject(xValue , Float.parseFloat(yValue)));
                    else
                        yObjectValuesArray.add(new DataObject(xValue ,Integer.parseInt(yValue)));
                }
                dataSeries.add(new ArrayOfObjects(yObjectValuesArray));
                break;

            default:
                return null;
        }

        if(dataSeries.isEmpty() || xAxis_categories.isEmpty())
            return null;

        return new HighChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()));
    }
}