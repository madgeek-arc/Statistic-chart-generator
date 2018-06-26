package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class Query {
    private List<Filter> filters;
    private List<Select> select;
    private String entity;
    private String profile;

    public Query() {}

    public Query(List<Filter> filters, List<Select> select, String entity, String profile) {
        this.filters = filters;
        this.select = select;
        this.entity = entity;
        this.profile = profile;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
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
}
