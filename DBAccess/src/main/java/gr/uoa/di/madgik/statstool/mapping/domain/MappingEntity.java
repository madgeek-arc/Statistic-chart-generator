package gr.uoa.di.madgik.statstool.mapping.domain;

import java.util.List;

public class MappingEntity {
    private String name;
    private String from;
    private String key;
    private List<MappingFilter> filters;
    private List<MappingField> fields;
    private List<String> relations;

    MappingEntity() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<MappingFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<MappingFilter> filters) {
        this.filters = filters;
    }

    public List<MappingField> getFields() {
        return fields;
    }

    public void setFields(List<MappingField> fields) {
        this.fields = fields;
    }

    public List<String> getRelations() {
        return relations;
    }

    public void setRelations(List<String> relations) {
        this.relations = relations;
    }
}
