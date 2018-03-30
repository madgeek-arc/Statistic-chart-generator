package gr.uoa.di.madgik.ChartDataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.ArrayOfArrays;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.DataSeries;
import org.junit.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.io.File;
import java.io.IOException;


@JsonTest
public class DataDeserializationTest {

    @Test
    public void DeserializeDataAbsData() throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        AbsData[] absData = mapper.readValue(new File("src/test/resources/unwrappedExpected.json"),AbsData[].class);

        assert absData != null;

        JsonNode jsonNode = mapper.readTree(new File("src/test/resources/wrappedExpected.json"));
        DataSeries deserialised = mapper.treeToValue(jsonNode,DataSeries.class);

        assert deserialised != null;
        assert deserialised.getDataSeries().get(0) instanceof ArrayOfArrays;

    }
}
