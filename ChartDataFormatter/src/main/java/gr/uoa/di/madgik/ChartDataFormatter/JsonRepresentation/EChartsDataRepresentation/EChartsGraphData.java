package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;

@JsonDeserialize(using= JsonDeserializer.None.class)
public class EChartsGraphData implements AbsData{
    
    @JsonProperty
    private ArrayList<EChartsGraphLink> links;
    @JsonProperty
    private ArrayList<EChartsDataObject> data;

    public EChartsGraphData(ArrayList<EChartsGraphLink> links, ArrayList<EChartsDataObject> data)
    {
        this.links = links;
        this.data = data;
    }

    @Override
    public ArrayList<EChartsDataObject> getData() { return data; }
    public void setData(ArrayList<EChartsDataObject> data) { this.data = data; }
    public ArrayList<EChartsGraphLink> getLinks() { return links; }
    public void setLinks(ArrayList<EChartsGraphLink> links) { this.links = links; }
}
