package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using= JsonDeserializer.None.class)
public class GraphData implements AbsData {
    
    @JsonProperty
    private ArrayList<String> keys;
    @JsonProperty
    private ArrayList<Object[]> data;

    public GraphData(ArrayList<String> keys, ArrayList<Object[]> data)
    {
        this.keys = keys;
        this.data = data;
    }

    @Override
    public ArrayList<Object[]> getData() { return data; }
    public void setData(ArrayList<Object[]> data) { this.data = data; }

    public ArrayList<String> getKeys() { return keys; }
    public void setKeys(ArrayList<String> keys) { this.keys = keys; }
}
