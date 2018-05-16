package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;
import gr.uoa.di.madgik.statstool.repositories.StatsRedisRepository;
import gr.uoa.di.madgik.statstool.repositories.StatsRepository;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchemaServiceImpl implements SchemaService{

    private StatsRepository statsRepository;

    private StatsRedisRepository statsRedisRepository;

    private Mapper mapper;

    public SchemaServiceImpl(StatsRepository statsRepository, StatsRedisRepository statsRedisRepository) {
        this.statsRepository = statsRepository;
        this.statsRedisRepository = statsRedisRepository;
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

    @Override
    public FieldValues getFieldValues(String field, String like) {
        List<String> fld = new ArrayList<>(Arrays.asList(field.split("\\.")));
        Field actualField = mapper.getFields().get(fld.get(fld.size()-2) + "." + fld.get(fld.size()-1));

        String query = "SELECT DISTINCT ";
        query += actualField.getColumn();
        query += " FROM " + actualField.getTable();
        if(!like.equals("")) {
            query += " WHERE";
            query += " lower(" + actualField.getColumn()  + ") LIKE \'%\' || ? || \'%\'";
        }
        query += " ORDER BY " + actualField.getColumn();
        System.out.println(query);


        String fullSqlQuery = statsRepository.getFullFieldsQuery(query, like.toLowerCase());
        List<String> values = statsRedisRepository.getValues(fullSqlQuery);
        if(values != null) {
            if(values.size() <= 70) {
                return new FieldValues(values.size(), values);
            } else {
                return new FieldValues(values.size(), null);
            }

        }
        values = statsRepository.executeFieldQuery(query, like.toLowerCase());
        statsRedisRepository.save(fullSqlQuery, values);

        if(values.size() <= 70) {
            return new FieldValues(values.size(), values);
        } else {
            return new FieldValues(values.size(), null);
        }
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
