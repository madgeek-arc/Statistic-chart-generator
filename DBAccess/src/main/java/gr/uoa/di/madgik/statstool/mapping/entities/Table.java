package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.List;

import gr.uoa.di.madgik.statstool.query.Filter;

public class Table {
    private String table;
    private String key;
    private List<Filter> filters;

    public Table(String table, String key, List<Filter> filters) {
        this.table = table;
        this.key = key;
        this.filters = filters;
    }

    public String getTable() {
        return table;
    }

    public String getKey() {
        return key;
    }

    public List<Filter> getFilters() {
        return filters;
    }
}
