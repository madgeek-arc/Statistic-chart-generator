package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(using = JsonDeserializer.None.class)
public class ArrayOfValues implements AbsData {

    @JsonProperty
    private List<Number> data;

    public ArrayOfValues() {
    }

    public ArrayOfValues(List<Number> data) {
        this.data = data;
    }

    @Override
    public List<Number> getData() {
        return data;
    }

    public void setData(List<Number> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
