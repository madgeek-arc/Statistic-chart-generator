package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchemaServiceImpl implements SchemaService{

    private Mapper mapper;

    public SchemaServiceImpl() {
        this.mapper = new Mapper();
    }

    @Override
    public List<String> getEntities() {
        List<String> entities = new ArrayList<>();

        for(Map.Entry<String, Entity> entity : mapper.getEntities().entrySet()) {
            entities.add(entity.getKey());
        }

        entities.sort(String::compareTo);
        return entities;
    }

    @Override
    public SchemaEntity getEntity(String entity) {
        Entity ent = mapper.getEntities().get(entity);
        SchemaEntity schemaEntity = new SchemaEntity(ent.getName(), ent.getFields());
        Set<String> path = new HashSet<>();
        path.add(entity);
        for(String relation : ent.getRelations()) {
            path.add(relation);
            schemaEntity.addRelation(createRelation(relation, path));
            path.remove(relation);
        }
        return schemaEntity;
    }

    private SchemaEntity createRelation(String entityName, Set<String> path) {
        Entity entity = mapper.getEntities().get(entityName);
        SchemaEntity schemaEntity = new SchemaEntity(entity.getName(), entity.getFields());
        for(String relation : entity.getRelations()) {
            if(!path.contains(relation)) {
                path.add(relation);
                schemaEntity.addRelation(createRelation(relation, path));
                path.remove(relation);
            }
        }
        return schemaEntity;
    }

}
