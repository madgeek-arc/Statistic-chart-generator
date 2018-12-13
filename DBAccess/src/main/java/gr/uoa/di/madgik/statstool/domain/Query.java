package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class Query {
    private List<FilterGroup> filters;
    private List<Select> select;
    private String entity;
    private String profile;
    private int limit;

    public Query() {}

    public Query(List<FilterGroup> filters, List<Select> select, String entity, String profile, int limit) {
        this.filters = filters;
        this.select = select;
        this.entity = entity;
        this.profile = profile;
        this.limit = limit;
    }

    public List<FilterGroup> getFilters() {
        return filters;
    }

    public void setFilters(List<FilterGroup> filters) {
        this.filters = filters;
    }

    public List<Select> getSelect() {
        return select;
    }

    public void setSelect(List<Select> select) {
        this.select = select;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
