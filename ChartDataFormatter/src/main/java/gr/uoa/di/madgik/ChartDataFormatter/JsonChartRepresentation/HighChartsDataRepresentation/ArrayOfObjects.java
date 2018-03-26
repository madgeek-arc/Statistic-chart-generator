package gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
@JsonDeserialize(using= JsonDeserializer.None.class)
public class ArrayOfObjects extends AbsData {

    @JsonProperty
    private ArrayList<DataObject> data;

    public ArrayOfObjects() {}

    public ArrayOfObjects(ArrayList<DataObject> data) {
        this.data = data;
    }

    @Override
    public ArrayList<DataObject> getData() {
        return data;
    }

    public void setData(ArrayList<DataObject> data) {
        this.data = data;
    }
}
