package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.*;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.domain.Profile;
import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchemaServiceImpl implements SchemaService{

    private final StatsService statsService;

    private final Mapper mapper;

    @Value("${statstool.result_limit}")
    private int RESULT_LIMIT;

    public SchemaServiceImpl(StatsService statsService, Mapper mapper) {
        this.statsService = statsService;
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

        schemaEntity.setRelations(ent.getRelations());

        return schemaEntity;
    }

    @Override
    public FieldValues getFieldValues(String profile, String field, String like) throws StatsServiceException {
        List<String> fld = new ArrayList<>(Arrays.asList(field.split("\\.")));
        String lastFld = fld.get(fld.size()-2) + "." + fld.get(fld.size()-1);

        List<Select> selects = new ArrayList<>();
        selects.add(new Select(lastFld, null, 1));
        Query query = new Query(null, null, null, selects, fld.get(fld.size()-2), profile, 0,null);

        List<Query> queries = new ArrayList<>();
        queries.add(query);


        List<Result> results = statsService.query(queries);
        if(results != null) {
            Result result = results.get(0);

            List<String> values = new ArrayList<>();
            for(List<?> val : result.getRows()) {
                if(val.get(0) != null && (like.equals("") || String.valueOf(val.get(0)).toLowerCase().contains(like.toLowerCase()))) {
                    values.add(String.valueOf(val.get(0)));
                }
            }

            if(values.size() <= RESULT_LIMIT) {
                return new FieldValues(values.size(), values);
            } else {
                return new FieldValues(values.size(), null);
            }
        } else {
            return new FieldValues(0, null);
        }
    }
}
