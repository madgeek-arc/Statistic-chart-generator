package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.ArrayList;
import java.util.List;

public class Entity {

    private String name;
    private List<EntityField> fields;
    private List<String> relations;

    public Entity(String name) {
        this.name = name;
        this.fields = new ArrayList<>();
        this.relations = new ArrayList<>();
    }

    public void addField(EntityField field) {
        fields.add(field);
    }

    public void addRelation(String relation) {
        relations.add(relation);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<EntityField> getFields() {
        return fields;
    }

    public void setFields(List<EntityField> fields) {
        this.fields = fields;
    }

    public List<String> getRelations() {
        return relations;
    }

    public void setRelations(List<String> relations) {
        this.relations = relations;
    }
}
