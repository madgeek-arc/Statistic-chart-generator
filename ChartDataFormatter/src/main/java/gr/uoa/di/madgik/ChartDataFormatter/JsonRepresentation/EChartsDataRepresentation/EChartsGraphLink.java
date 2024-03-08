package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = JsonDeserializer.None.class)
public class EChartsGraphLink {

    @JsonProperty
    private String source;
    @JsonProperty
    private String target;
    @JsonProperty
    private Number value;

    public EChartsGraphLink(String source, String target, Number value) {
        this.source = source;
        this.target = target;
        this.value = value;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return this.target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Number getValue() {
        return this.value;
    }

    public void setValue(Number value) {
        this.value = value;
    }

}
