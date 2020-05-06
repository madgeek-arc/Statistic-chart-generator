package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Data object as shown in the eCharts <a href="https://www.echartsjs.com/en/option.html#series-pie.data">library</a>
 */
public class EChartsDataObject {

    @JsonProperty
    private String name;
    @JsonProperty
    private Number value;

    public EChartsDataObject(String name,Number value){
        this.name = name;
        this.value = value;
    }

    public EChartsDataObject() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }
}
