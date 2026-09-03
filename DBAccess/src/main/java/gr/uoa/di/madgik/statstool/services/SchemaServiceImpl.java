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

        // Push the "like" substring match down into SQL as a `contains` filter (which emits
        // lower(col) LIKE '%?%' with a lower-cased bind) instead of filtering every distinct
        // value in Java.
        List<FilterGroup> filters = null;
        if (like != null && !like.isEmpty()) {
            Filter likeFilter = new Filter(lastFld, "contains", Collections.singletonList(like), null);
            filters = Collections.singletonList(new FilterGroup(new ArrayList<>(Collections.singletonList(likeFilter)), "AND"));
        }

        // Bound the query: RESULT_LIMIT + 1 rows is enough to decide "return the list" vs
        // "too many, return count only" below, and keeps an unbounded distinct-value dump
        // from overflowing the result cache.
        Query query = new Query(null, null, filters, selects, fld.get(fld.size()-2), profile, RESULT_LIMIT + 1, null, true);

        List<Query> queries = new ArrayList<>();
        queries.add(query);


        List<Result> results = statsService.query(queries);
        if(results != null) {
            Result result = results.get(0);

            List<String> values = new ArrayList<>();
            for(List<?> val : result.getRows()) {
                values.add(String.valueOf(val.get(0)));
            }

            if(values.size() <= RESULT_LIMIT) {
                return new FieldValues(values.size(), values);
            } else {
                // The list query is capped at RESULT_LIMIT + 1, so values.size() here is just
                // the cap. Fire one lightweight COUNT(DISTINCT) query (same filter) so the
                // response still reports the real number of matching distinct values.
                return new FieldValues(countDistinctValues(profile, fld, lastFld, filters), null);
            }
        } else {
            return new FieldValues(0, null);
        }
    }

    private int countDistinctValues(String profile, List<String> fld, String lastFld,
                                    List<FilterGroup> filters) throws StatsServiceException {
        List<Select> selects = new ArrayList<>();
        // "count" compiles to COUNT(DISTINCT <col>), matching the DISTINCT semantics of the
        // list query above.
        selects.add(new Select(lastFld, "count", 1));
        Query countQuery = new Query(null, null, filters, selects,
                fld.get(fld.size() - 2), profile, 1, null, true);

        List<Result> results = statsService.query(new ArrayList<>(Collections.singletonList(countQuery)));
        if (results != null && !results.isEmpty() && results.get(0) != null
                && results.get(0).getRows() != null && !results.get(0).getRows().isEmpty()) {
            Object cell = results.get(0).getRows().get(0).get(0);
            if (cell != null) {
                try {
                    return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(String.valueOf(cell).trim()));
                } catch (NumberFormatException ignored) {
                    // fall through to the sentinel below
                }
            }
        }
        return RESULT_LIMIT + 1; // count unavailable: still signal "over the limit"
    }
}
