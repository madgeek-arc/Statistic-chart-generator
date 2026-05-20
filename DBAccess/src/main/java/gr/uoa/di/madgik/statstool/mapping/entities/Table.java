package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Filter;

public class Table {
    private final String table;
    private final String key;
    private final List<Filter> filters;
    private final String description;

    public Table(String table, String key, List<Filter> filters) {
        this(table, key, filters, null);
    }

    public Table(String table, String key, List<Filter> filters, String description) {
        this.table = table;
        this.key = key;
        this.filters = filters;
        this.description = description;
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

    public String getDescription() {
        return description;
    }
}
