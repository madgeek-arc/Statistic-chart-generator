package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.GoogleChartsJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;

import java.util.*;

/**
 * Extends DataFormatter handling the formation of data returned from DBAccess
 * to a format convenient for GoogleCharts library.
 * @see DataFormatter
 */
public class GoogleChartsDataFormatter extends DataFormatter {

    /**
     * {@inheritDoc}
     *
     * @return A {@link GoogleChartsJsonResponse} ready to be passed as a response body.
     */
    @Override
    public GoogleChartsJsonResponse toJsonResponse(List<Result> dbAccessResults, List<SupportedChartTypes> chartsType) throws DataFormationException {

        /* ASSUMPTIONS:
         * ~ Results have a [x,y] format.
         * ~ Dates are returned as a String format
         */
        if(chartsType != null)
            throw new DataFormationException("Expected null chartsType List: Google Charts data is independent of type");
        if(dbAccessResults.size() == 1)
            return singleToGoogleChartsJsonResponse(dbAccessResults.get(0));

        List<List<Object>> formattedDataTable = new ArrayList<>();

        AbstractMap.SimpleEntry<List<String>, List<HashMap<String, String>>> xAxis_CategoriesToXYMapping
                = assembleXAxisCategoriesToXYMapping(dbAccessResults);

        //A sorted List with all the possible x values occurring from the Queries.
        List<String> xAxis_Categories = xAxis_CategoriesToXYMapping.getKey();

        //A List which holds every Result in a HashMap, mapping y values to x values ([x,y]).
        List<HashMap<String, String>> allRowsXValueToYValueMappings = xAxis_CategoriesToXYMapping.getValue();


        // Create the data table row by mapping to all the possible xValues their corresponding yValues.
        // Fill with null if need be.
        for (String xValue : xAxis_Categories) {

            ArrayList<Object> ValuesArray = new ArrayList<>();
            ValuesArray.add(xValue);

            for (HashMap<String, String> xValuetoY : allRowsXValueToYValueMappings) {
                if (xValuetoY.containsKey(xValue)) {

                    String yValue = xValuetoY.get(xValue);
                    if (yValue.contains("."))
                        ValuesArray.add(Float.parseFloat(yValue));
                    else
                        ValuesArray.add(Integer.parseInt(yValue));
                } else
                    ValuesArray.add(null);
            }
            formattedDataTable.add(ValuesArray);
        }

        if(formattedDataTable.isEmpty() || xAxis_Categories.isEmpty())
            return null;

        return new GoogleChartsJsonResponse(formattedDataTable);
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

            for (ArrayList<String> row : result.getRows() ){

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

    private GoogleChartsJsonResponse singleToGoogleChartsJsonResponse(Result result){

        List<List<Object>> formattedDataTable = new ArrayList<>();

        if(result.getRows().isEmpty())
            return null;

        for (ArrayList<String> row : result.getRows()) {
            ArrayList<Object> valuesArray = new ArrayList<>();

            String xValue = row.get(0);
            String yValue = row.get(1);

            valuesArray.add(xValue);
            if (yValue.contains("."))
                valuesArray.add(Float.parseFloat(yValue));
            else
                valuesArray.add(Integer.parseInt(yValue));

            formattedDataTable.add(valuesArray);
        }



        return new GoogleChartsJsonResponse(formattedDataTable);
    }
}
