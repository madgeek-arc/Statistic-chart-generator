import JsonChartRepresentation.HighChartsDataRepresentation.AbsData;
import JsonChartRepresentation.HighChartsDataRepresentation.ArrayOfTuples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;


import java.io.IOException;
import java.util.ArrayList;

@JsonTest
public class DataDeserializationTest {

    @Test
    public void DeserializeData() throws IOException {
        // Data tuples as Number[]
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

       /*// Data tuples as Class
        ArrayList<DataTuple> arrayList = new ArrayList<>();
        Number[] array = new Number[2];

        array[0] = 260064;
        array[1] = 0;
        arrayList.add(new DataTuple(array));

        array = new Number[2];
        array[0] = 269568;
        array[1] = 0.4;
        arrayList.add(new DataTuple(array));

        array = new Number[2];
        array[0] = 288576;
        array[1] = 0.25;
        arrayList.add(new DataTuple(array));

        array = new Number[2];
        array[0] = 315360;
        array[1] = 1.66;
        arrayList.add(new DataTuple(array));

        array = new Number[2];
        array[0] = 323136;
        array[1] = 1.8;
        arrayList.add(new DataTuple(array));*/

        ArrayOfTuples tuples = new ArrayOfTuples(arrayList);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        String json = mapper.writeValueAsString(tuples);

        System.out.println("Json to deserialise: "+json);
        JsonNode jsonNode = mapper.readTree(json);
        AbsData deserialised = mapper.treeToValue(jsonNode,AbsData.class);

        assert deserialised != null;

    }
}
