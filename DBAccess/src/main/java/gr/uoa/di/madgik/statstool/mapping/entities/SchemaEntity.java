package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.ArrayList;
import java.util.List;

public class SchemaEntity {
    private String name;
    private List<EntityField> fields;
    private List<SchemaEntity> relations;

    public SchemaEntity(String name, List<EntityField> fields) {
        this.name = name;
        this.fields = fields;
        this.relations = new ArrayList<>();
    }

    public void addRelation(SchemaEntity entity) {
        relations.add(entity);
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

    public List<SchemaEntity> getRelations() {
        return relations;
    }

    public void setRelations(List<SchemaEntity> relations) {
        this.relations = relations;
    }
}
