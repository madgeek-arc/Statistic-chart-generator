package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.statstool.domain.Query;

import java.util.List;

public class VerboseRawDataSerie {
    private Query query;
    @JsonProperty("result")
    private List<VerboseRawDataRow> rows;

    public VerboseRawDataSerie(Query query, List<VerboseRawDataRow> rows) {
        this.query = query;
        this.rows = rows;
    }

    public VerboseRawDataSerie() {
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public List<VerboseRawDataRow> getRows() {
        return rows;
    }

    public void setRows(List<VerboseRawDataRow> rows) {
        this.rows = rows;
    }
}
