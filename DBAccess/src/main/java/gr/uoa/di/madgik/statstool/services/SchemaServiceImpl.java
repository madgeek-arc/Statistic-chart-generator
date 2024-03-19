package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.domain.Profile;
import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;
import info.debatty.java.stringsimilarity.Cosine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SchemaServiceImpl implements SchemaService {

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

        for (Map.Entry<String, Entity> entity : mapper.getEntities(profile).entrySet()) {
            entities.add(entity.getKey());
        }

        entities.sort(String::compareTo);
        return entities;
    }

    @Override
    public SchemaEntity getEntity(String profile, String entity) {
        Entity ent = mapper.getEntities(profile).get(entity);
        if (ent == null) {
            return null;
        }
        SchemaEntity schemaEntity = new SchemaEntity(ent.getName(), ent.getFields());

        schemaEntity.setRelations(ent.getRelations());

        return schemaEntity;
    }

    @Override
    public FieldValues getFieldValues(String profile, String field, String like) throws StatsServiceException {
        List<String> fld = new ArrayList<>(Arrays.asList(field.split("\\.")));
        String lastFld = fld.get(fld.size() - 2) + "." + fld.get(fld.size() - 1);

        List<Select> selects = new ArrayList<>();
        selects.add(new Select(lastFld, null, 1));
        Query query = new Query(null, null, null, selects, fld.get(fld.size() - 2), profile, 0, null, true);

        List<Query> queries = new ArrayList<>();
        queries.add(query);


        List<Result> results = statsService.query(queries);
        if (results != null) {
            Result result = results.get(0);

            List<String> values = new ArrayList<>();
            like = like.trim().toLowerCase();
            for (List<?> val : result.getRows()) {
                values.add(String.valueOf(val.get(0)));
            }
            values = sortByRelevance(values, like);

            if (values.size() <= RESULT_LIMIT) {
                return new FieldValues(values.size(), values);
            } else {
                return new FieldValues(RESULT_LIMIT, values.subList(0, RESULT_LIMIT));
            }
        } else {
            return new FieldValues(0, null);
        }
    }

    public static List<String> sortByRelevance(List<String> items, String keyword) {
        Map<Double, String> sorted = new TreeMap<>();
        Cosine l = new Cosine();

        for (String obj : items) {
            String value = obj.toLowerCase();
            if (!StringUtils.hasText(value)) {
                continue;
            }

            double score = new Random().nextDouble() / 1_000_000; // init as a small random to avoid distance conflicts
            score += l.distance(value, keyword);
            sorted.put(score, obj);
        }

        return new ArrayList<>(sorted.values());
    }
}
