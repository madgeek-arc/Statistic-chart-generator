package gr.uoa.di.madgik.statstool.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        QueryWithParameters that = (QueryWithParameters) o;

        return new EqualsBuilder()
                .append(query, that.query)
                .append(parameters, that.parameters)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(query)
                .append(parameters)
                .toHashCode();
    }
}
