package gr.uoa.di.madgik.statstool.mapping.domain;

import java.util.List;

public class Mapping {
    private List<MappingEntity> entities;
    private List<MappingRelation> relations;

    Mapping() {}

    public List<MappingEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<MappingEntity> entities) {
        this.entities = entities;
    }

    public List<MappingRelation> getRelations() {
        return relations;
    }

    public void setRelations(List<MappingRelation> relations) {
        this.relations = relations;
    }
}
