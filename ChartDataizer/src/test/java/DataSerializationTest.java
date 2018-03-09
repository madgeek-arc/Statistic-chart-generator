import JsonChartRepresentation.HighChartsDataRepresentation.AbsData;
import JsonChartRepresentation.HighChartsDataRepresentation.ArrayOfTuples;
import JsonChartRepresentation.HighChartsDataRepresentation.DataSeries;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;


import java.util.ArrayList;

@JsonTest
public class DataSerializationTest {

    private String wrappedExpected = "{\"series\":[{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}," +
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}," +
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}]}";
    private String unwrappedExpected = "[{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}," +
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]},"+
            "{\"data\":[[260064,0],[269568,0.4],[288576,0.25],[315360,1.66],[323136,1.8]]}]";

    @Test
    public void SerializeData() throws JsonProcessingException {

        ArrayList<AbsData> dataToPass = new ArrayList<>();

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

        dataToPass.add(tuples);
        dataToPass.add(tuples);
        dataToPass.add(tuples);

        DataSeries packagedData = new DataSeries(dataToPass);


        ObjectMapper mapper = new ObjectMapper();
        String wrapped = mapper.writeValueAsString(packagedData);
        mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE,true);
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE,false);

        assert wrapped.compareTo(wrappedExpected) == 0;
        String notWrapped = mapper.writeValueAsString(packagedData.getDataSeries());
        assert notWrapped.compareTo(unwrappedExpected) == 0;

    }
}
