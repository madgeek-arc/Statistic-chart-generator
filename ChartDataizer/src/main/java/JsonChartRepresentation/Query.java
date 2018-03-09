package JsonChartRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class representing a Query
 */
public class Query {

    @JsonProperty
    private String query;

    public Query(String query) {
        this.query = query;
    }

    public Query() {}

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
