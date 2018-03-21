package gr.uoa.di.madgik.ChartDataizer.DataFormatter;

import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.statstool.db.Result;
import org.springframework.lang.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class DataFormatter {

    public DataFormatter() { }

    public HighChartsJsonResponse toHighchartsJsonResponse(@NonNull List<Result> dbAccessResults,@NonNull String chartType){

        /* ASSUMPTIONS:
         * ~ Results have a [x,y] format.
         * ~ On each Result there will be every available X value
         *   paired with a Y value, which might be null.
         * ~ Y values are either float or int, I do not account for Date yet
         */

        LinkedHashMap<String,Integer> xAxis_categories = new LinkedHashMap<>();
        ArrayList<AbsData> dataSeries = new ArrayList<>();

        for(Result result : dbAccessResults) {
            if(result.getRows().isEmpty())
                break;

            switch (SupportedChartTypes.valueOf(chartType)) {
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
        }

        if(dataSeries.isEmpty() || xAxis_categories.isEmpty())
            return null;

        return new HighChartsJsonResponse(dataSeries,new ArrayList<>(xAxis_categories.keySet()));
    }
}
