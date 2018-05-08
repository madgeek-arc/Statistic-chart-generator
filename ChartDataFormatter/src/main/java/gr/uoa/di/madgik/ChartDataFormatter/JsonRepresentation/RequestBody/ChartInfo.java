package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.statstool.domain.Query;

public class ChartInfo {

    @JsonProperty(value = "type")
    private String chartType;
    @JsonProperty
    private Query query;

    public ChartInfo() {}

    public ChartInfo(String chartType, Query query) {
        this.chartType = chartType;
        this.query = query;
    }

    public String getChartType() { return chartType; }

    public void setChartType(String chartType) { this.chartType = chartType; }

    public Query getQuery() { return query; }

    public void setQuery(Query query) { this.query = query; }
}
