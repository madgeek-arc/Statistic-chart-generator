package gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.statstool.query.Query;
import java.util.ArrayList;

/**
 * Class holding the necessary information for the ChartDataizer to form the query results
 */
public class RequestInfo {

    @JsonProperty
    private String library;
    @JsonProperty(value = "type")
    private String chartType;
    @JsonProperty
    private ArrayList<Query> queries;

    public RequestInfo(String library, String chartType, ArrayList<Query> queries) {
        this.library = library;
        this.chartType = chartType;
        this.queries = queries;
    }

    public RequestInfo() {}

    public String getLibrary() {
        return library;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public String getChartType() {
        return chartType;
    }

    public void setChartType(String chartType) {
        this.chartType = chartType;
    }

    public ArrayList<Query> getQueries() {
        return queries;
    }

    public void setQueries(ArrayList<Query> queries) {
        this.queries = queries;
    }
}
