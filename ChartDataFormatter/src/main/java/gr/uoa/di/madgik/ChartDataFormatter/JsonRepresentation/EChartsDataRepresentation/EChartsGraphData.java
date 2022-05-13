package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;

@JsonDeserialize(using= JsonDeserializer.None.class)
public class EChartsGraphData implements AbsData{
    
    @JsonProperty
    private List<EChartsGraphLink> links;
    @JsonProperty
    private List<EChartsDataObject> data;

    public EChartsGraphData(List<EChartsGraphLink> links, List<EChartsDataObject> data)
    {
        this.links = links;
        this.data = data;
    }

    @Override
    public List<EChartsDataObject> getData() { return data; }
    public void setData(List<EChartsDataObject> data) { this.data = data; }
    public List<EChartsGraphLink> getLinks() { return links; }
    public void setLinks(List<EChartsGraphLink> links) { this.links = links; }
}
