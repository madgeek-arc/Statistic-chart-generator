package JsonChartRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;


@JsonDeserialize(using=JsonDeserializer.None.class)
public class ArrayOfTuples extends AbsData {

    @JsonProperty
    private ArrayList<Number[]> data;

    public ArrayOfTuples() {}

    public ArrayOfTuples(ArrayList<Number[]> data) {
        this.data = data;
    }

    @Override
    public ArrayList<Number[]> getData() {
        return data;
    }

    public void setData(ArrayList<Number[]> data) {
        this.data = data;
    }

//    @JsonProperty
//    private ArrayList<DataTuple> data;
//
//    public ArrayOfTuples(ArrayList<DataTuple> data) {
//        this.data = data;
//    }
//
//    public ArrayOfTuples() {}
//
//    @Override
//    public ArrayList<DataTuple> getData() {
//        return data;
//    }
//
//    public void setData(ArrayList<DataTuple> data) {
//        this.data = data;
//    }
}
