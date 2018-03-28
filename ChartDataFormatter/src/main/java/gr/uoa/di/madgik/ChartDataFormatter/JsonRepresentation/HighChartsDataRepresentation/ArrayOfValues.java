package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;

@JsonDeserialize(using= JsonDeserializer.None.class)
public class ArrayOfValues extends AbsData {

    @JsonProperty
    private ArrayList<Number> data;

    public ArrayOfValues() {}

    public ArrayOfValues(ArrayList<Number> data) {
        this.data = data;
    }

    @Override
    public ArrayList<Number> getData() {
        return data;
    }

    public void setData(ArrayList<Number> data) {
        this.data = data;
    }
}
