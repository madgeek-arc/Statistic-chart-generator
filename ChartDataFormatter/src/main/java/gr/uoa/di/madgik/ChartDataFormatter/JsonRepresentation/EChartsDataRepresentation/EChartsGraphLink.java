package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using= JsonDeserializer.None.class)
public class EChartsGraphLink {
    
    @JsonProperty
    private String source;
    @JsonProperty
    private String target;

    public EChartsGraphLink(String source, String target)
    {
        this.source = source;
        this.target = target;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    
}
