package JsonChartRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

/**
 * Class holding the necessary information for the DBAccess to return the query results
 */
public class QueryInfo {

    @JsonProperty
    private String library;
    @JsonProperty
    private String chartType;
    @JsonProperty
    private ArrayList<Query> queries;

    public QueryInfo(String library, String chartType, ArrayList<Query> queries) {
        this.library = library;
        this.chartType = chartType;
        this.queries = queries;
    }

    public QueryInfo() {}

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
