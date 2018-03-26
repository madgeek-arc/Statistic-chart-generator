package gr.uoa.di.madgik.ChartDataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation.AbsData;
import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation.ArrayOfArrays;
import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation.DataSeries;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation.HighChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.JsonResponse;
import org.junit.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

@JsonTest
public class DataSerializationTest {

    private String wrappedExpected = "{\"series\":[{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}," +
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}," +
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}]}";
    private String unwrappedExpected = "[{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}," +
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]},"+
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}]";
    private String HighchartsJsonResponseExpected = "{\"xAxis_categories\":null,\"series\":[{\"data\":[1,0,4]},{\"data\":[5,7,3]}]}";

    @Test
    public void SerializeData() throws JsonProcessingException {

        ArrayList<AbsData> dataToPass = new ArrayList<>();
        ArrayList<Number[]> arrayList = new ArrayList<>();
        Number[] array = new Number[2];

        array[0] = 260064;
        array[1] = 0;
        arrayList.add(array);

        array = new Number[2];
        array[0] = 269568;
        array[1] = 0.4;
        arrayList.add(array);

        array = new Number[2];
        array[0] = 288576;
        array[1] = 0.25;
        arrayList.add(array);

        array = new Number[2];
        array[0] = 315360;
        array[1] = 1.66;
        arrayList.add(array);

        array = new Number[2];
        array[0] = 323136;
        array[1] = 1.8;
        arrayList.add(array);

        ArrayOfArrays arrayOfArrays = new ArrayOfArrays(arrayList);

        dataToPass.add(arrayOfArrays);
        dataToPass.add(arrayOfArrays);
        dataToPass.add(arrayOfArrays);

        DataSeries packagedData = new DataSeries(dataToPass);

        ObjectMapper mapper = new ObjectMapper();
        String wrapped = mapper.writeValueAsString(packagedData);
        mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE,true);
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE,false);

        assert wrapped.compareTo(wrappedExpected) == 0;
        String notWrapped = mapper.writeValueAsString(packagedData.getDataSeries());
        assert notWrapped.compareTo(unwrappedExpected) == 0;

    }

    @Test
    public void SerializeArrayOfValues() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        AbsData asdf = mapper.readValue("{\"data\": [1, 0, 4]}", AbsData.class);

        assert asdf!=null;
    }
    @Test
    public void SerializeDataJsonResponse() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        AbsData[] series = mapper.readValue(new File("src/test/resources/public/jsonFiles/chartdataizerToHtml.json"), AbsData[].class);
        assert series != null;

        JsonResponse jsonResponse =  new HighChartsJsonResponse(Arrays.asList(series),null);
        String jsonString = mapper.writeValueAsString(jsonResponse);
        assert jsonString.compareTo(HighchartsJsonResponseExpected) == 0;


    }
}
