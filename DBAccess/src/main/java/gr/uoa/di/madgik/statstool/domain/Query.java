package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class Query {
    private String name;
    private List<Object> parameters;
    private List<FilterGroup> filters;
    private List<Select> select;
    private String entity;
    private String profile;
    private int limit;
    private String orderBy;

    boolean useCache = true;

    public Query() {
    }

    public Query(String name, List<Object> parameters, List<FilterGroup> filters, List<Select> select, String entity, String profile, int limit, String orderBy, boolean useCache) {
        this.name = name;
        this.parameters = parameters;
        this.filters = filters;
        this.select = select;
        this.entity = entity;
        this.profile = profile;
        this.limit = limit;
        this.orderBy = orderBy;
        this.useCache = useCache;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
    }

    @Override
    public String toString() {
        return "Query{" +
                "name='" + name + '\'' +
                ", parameters=" + parameters +
                ", filters=" + filters +
                ", select=" + select +
                ", entity='" + entity + '\'' +
                ", profile='" + profile + '\'' +
                ", limit=" + limit +
                ", orderBy='" + orderBy + '\'' +
                '}';
    }
}
