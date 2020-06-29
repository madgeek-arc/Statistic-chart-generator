package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class QueryWithParameters {
    private String query;
    private List<Object> parameters;

    public QueryWithParameters() {
    }

    public QueryWithParameters(String query, List<Object> parameters) {
        this.query = query;
        this.parameters = parameters;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "QueryWithParameters{" +
                "query='" + query + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
