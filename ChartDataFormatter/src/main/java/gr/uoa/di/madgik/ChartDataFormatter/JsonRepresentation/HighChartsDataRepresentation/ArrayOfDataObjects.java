package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
@JsonDeserialize(using= JsonDeserializer.None.class)
public class ArrayOfDataObjects implements AbsData {

    @JsonProperty
    private ArrayList<DataObject> data;

    public ArrayOfDataObjects() {}

    public ArrayOfDataObjects(ArrayList<DataObject> data) {
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
