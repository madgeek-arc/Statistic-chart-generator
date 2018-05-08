package gr.uoa.di.madgik.ChartDataFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import org.junit.Test;
import gr.uoa.di.madgik.statstool.domain.Result;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DbAccessResultsTest {

    @Test
    public void getResultsFromDBAccess() throws IOException, CantDealWithException, DataFormatter.DataFormationException {

        ObjectMapper mapper = new ObjectMapper();
        Result queryResult = mapper.readValue(new File("src/test/resources/highcharts/testFiles/result1.json"), Result.class);
        List<Result> resultList = new ArrayList<>();
        resultList.add(queryResult);
        for(ArrayList<String> row :queryResult.getRows())
            if(row.size() != 2)
                throw new CantDealWithException("Row size different than 2");

        List<SupportedChartTypes> chartList = new ArrayList<>();
        chartList.add(SupportedChartTypes.pie);

        HighChartsJsonResponse response = new HighChartsDataFormatter().toJsonResponse(resultList, chartList);
        mapper.configure(SerializationFeature.INDENT_OUTPUT,true);

        assert response != null;
        assert !response.getDataSeries().isEmpty();
        assert !response.getxAxis_categories().isEmpty();
        if (response.getDataSeries().get(0) instanceof ArrayOfValues)
            assert ((ArrayOfValues)response.getDataSeries().get(0)).getData().size() == response.getxAxis_categories().size();
        else if (response.getDataSeries().get(0) instanceof ArrayOfObjects)
            assert ((ArrayOfObjects)response.getDataSeries().get(0)).getData().size() == response.getxAxis_categories().size();
        else
            assert false;

        System.out.println("DBAccess Response: "+mapper.writeValueAsString(response));

    }

    public class CantDealWithException extends Exception{
        public CantDealWithException() { }

        public CantDealWithException(String s) {
            super(s);
        }

        public CantDealWithException(String s, Throwable throwable) {
            super(s, throwable);
        }

        public CantDealWithException(Throwable throwable) {
            super(throwable);
        }

        public CantDealWithException(String s, Throwable throwable, boolean b, boolean b1) {
            super(s, throwable, b, b1);
        }
    }
}
