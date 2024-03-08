package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(using = JsonDeserializer.None.class)
public class GraphData implements AbsData {

    @JsonProperty
    private List<String> keys;
    @JsonProperty
    private List<Object[]> data;

    public GraphData(List<String> keys, List<Object[]> data) {
        this.keys = keys;
        this.data = data;
    }

    @Override
    public List<Object[]> getData() {
        return data;
    }

    public void setData(List<Object[]> data) {
        this.data = data;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }
}
