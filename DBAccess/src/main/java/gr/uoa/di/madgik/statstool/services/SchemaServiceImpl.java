package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.mapping.NewMapper;
import gr.uoa.di.madgik.statstool.mapping.domain.Profile;
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

    //private StatsService statsService;

    //private Mapper mapper;
    private NewMapper mapper;

    /*
    public SchemaServiceImpl(StatsService statsService) {
        this.statsService = statsService;
        this.mapper = new Mapper();
    }
    */

    /*
    public SchemaServiceImpl(StatsRepository statsRepository, StatsRedisRepository statsRedisRepository) {
        this.statsRepository = statsRepository;
        this.statsRedisRepository = statsRedisRepository;
        this.mapper = new Mapper();
    }
    */

    public SchemaServiceImpl(StatsRepository statsRepository, StatsRedisRepository statsRedisRepository, NewMapper mapper) {
        this.statsRepository = statsRepository;
        this.statsRedisRepository = statsRedisRepository;
        this.mapper = mapper;
    }

    @Override
    public List<Profile> getProfiles() {
        return mapper.getProfiles();
    }

    @Override
    public List<String> getEntities(String profile) {
        List<String> entities = new ArrayList<>();

        for(Map.Entry<String, Entity> entity : mapper.getEntities(profile).entrySet()) {
            entities.add(entity.getKey());
        }

        entities.sort(String::compareTo);
        return entities;
    }

    @Override
    public SchemaEntity getEntity(String profile, String entity) {
        Entity ent = mapper.getEntities(profile).get(entity);
        if(ent == null) {
            return null;
        }
        SchemaEntity schemaEntity = new SchemaEntity(ent.getName(), ent.getFields());
        Set<String> path = new HashSet<>();
        path.add(entity);
        for(String relation : ent.getRelations()) {
            path.add(relation);
            schemaEntity.addRelation(createRelation(profile, relation, path));
            path.remove(relation);
        }
        return schemaEntity;
    }

    @Override
    public FieldValues getFieldValues(String profile, String field, String like) {
        List<String> fld = new ArrayList<>(Arrays.asList(field.split("\\.")));
        Field actualField = mapper.getFields(profile).get(fld.get(fld.size()-2) + "." + fld.get(fld.size()-1));

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

    /*
    @Override
    public FieldValues getFieldValues(String field, String like) {
        List<String> fld = new ArrayList<>(Arrays.asList(field.split("\\.")));
        String lastFld = fld.get(fld.size()-2) + "." + fld.get(fld.size()-1);
        System.out.println(lastFld);
        List<Select> selects = new ArrayList<>();
        selects.add(new Select(lastFld, null, 1));
        List<Filter> filters = new ArrayList<>();
        if(!like.equals("")) {
            List<String> values = new ArrayList<>();
            values.add(like);
            filters.add(new Filter(lastFld, "contains", values, null));
        }
        List<Query> queries = new ArrayList<>();
        Query query = new Query(filters, selects, fld.get(fld.size()-2));

        try {
            System.out.println(new ObjectMapper().writeValueAsString(query));
        } catch (Exception e) {
            e.printStackTrace();
        }
        queries.add(query);
        List<Result> results = statsService.query(queries);
        if(results != null) {
            Result result = results.get(0);
            List<String> values = new ArrayList<>();

            for(List<String> val : result.getRows()) {
                values.add(val.get(0));
            }

            if(values.size() <= 70) {
                return new FieldValues(values.size(), values);
            } else {
                return new FieldValues(values.size(), null);
            }
        } else {
            return new FieldValues(0, null);
        }
    }
    */


    private SchemaEntity createRelation(String profile, String entityName, Set<String> path) {
        Entity entity = mapper.getEntities(profile).get(entityName);
        SchemaEntity schemaEntity = new SchemaEntity(entity.getName(), entity.getFields());
        for(String relation : entity.getRelations()) {
            if(!path.contains(relation)) {
                path.add(relation);
                schemaEntity.addRelation(createRelation(profile, relation, path));
                path.remove(relation);
            }
        }
        return schemaEntity;
    }

}
