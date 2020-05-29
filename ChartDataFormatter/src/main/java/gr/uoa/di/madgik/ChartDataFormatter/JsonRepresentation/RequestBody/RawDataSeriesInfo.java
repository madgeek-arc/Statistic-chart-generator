package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.statstool.domain.Query;

public class RawDataSeriesInfo {

    @JsonProperty
    private Query query;

    public RawDataSeriesInfo(Query query) {
        this.query = query;
    }

    public RawDataSeriesInfo() {
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }
}
