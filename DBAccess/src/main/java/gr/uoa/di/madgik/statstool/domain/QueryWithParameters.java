package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class QueryWithParameters {
    private String query;
    private List<Object> parameters;

    public QueryWithParameters() {
    }

    public QueryWithParameters(String query, List<Object> parameters) {
        this.query = query.endsWith(";")?query.substring(0, query.length() - 1):query;
        this.parameters = parameters;
    }

    public String getQuery() {
        return query;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "QueryWithParameters{" +
                "query='" + query + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
