package gr.uoa.di.madgik.ChartDataizer.DataFormatter;

import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.statstool.db.Result;
import org.springframework.lang.NonNull;

import java.util.*;

public class DataFormatter {

    public DataFormatter() { }

    //TODO Testing on extreme results
    public HighChartsJsonResponse toHighchartsJsonResponse(@NonNull List<Result> dbAccessResults,@NonNull SupportedChartTypes chartType){

        /* ASSUMPTIONS:
         * ~ Results have a [x,y] format.
         * ~ Y values are either float or int, I do not account for Date yet
         */

        LinkedHashSet<String> xAxis_categories = new LinkedHashSet<>();
        ArrayList<HashMap<String,String>> allRowsXValueToYValueMappings = new ArrayList<>();

        //Create a Map where its keys are all the possible xValues from the Results
        for(Result result : dbAccessResults) {
            if (result.getRows().isEmpty())
                break;

            HashMap<String, String> rowXValueToYValueMapping = new HashMap<>();

            for (int i = 0; i<result.getRows().size(); i++){
                ArrayList<String> row = result.getRows().get(i);

                String xValue = row.get(0);
                String yValue = row.get(1);

                //Finding an xValue and registering it in the xAxis_categories
                if (!xAxis_categories.contains(xValue))
                    xAxis_categories.add(xValue);

                rowXValueToYValueMapping.put(xValue,yValue);
            }
            allRowsXValueToYValueMappings.add(rowXValueToYValueMapping);
        }

        ArrayList<String> sortedXAxis_Categories = new ArrayList<>(xAxis_categories);
        sortedXAxis_Categories.sort(String::compareToIgnoreCase);

        System.out.println("XAxis_Categories: "+xAxis_categories.toString());

        ArrayList<AbsData> dataSeries = new ArrayList<>();

        //Map to all the possible xValues their corresponding yValues. Fill with null if need be
        for (HashMap<String, String> xValuetoY : allRowsXValueToYValueMappings)
            switch (chartType) {
                case area:
                case bar:
                case column:
                case line:
                    ArrayList<Number> yValuesArray = new ArrayList<>();

                    for (String xValue : sortedXAxis_Categories) {
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

                    for (String xValue : sortedXAxis_Categories) {
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
                    return null;
            }


        if(dataSeries.isEmpty() || xAxis_categories.isEmpty())
            return null;

        return new HighChartsJsonResponse(dataSeries,sortedXAxis_Categories);
    }
}
