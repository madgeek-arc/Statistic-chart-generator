package gr.uoa.di.madgik.statstool.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.mapping.domain.*;
import org.springframework.stereotype.Component;

import gr.uoa.di.madgik.statstool.mapping.entities.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Select;

@Component
public class NewMapper {

    private final HashMap<String, Table> tables = new HashMap<>();
    private final HashMap<String, Field> fields = new HashMap<>();
    private final HashMap<String, List<Join>> relations = new HashMap<>();

    private final HashMap<String, HashMap<String, Entity>> entities = new HashMap<>();

    private final List<Profile> profiles = new ArrayList<>();

    private String primaryProfile;

    public NewMapper() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            MappingProfile[] mappings = mapper.readValue(getClass().getClassLoader().getResource("mappings.json"), MappingProfile[].class);
            for(MappingProfile mappingProfile : mappings) {
                profiles.add(new Profile(mappingProfile.getName(), mappingProfile.getDescription()));
                if(mappingProfile.isPrimary()) {
                    initMapper(mappingProfile.getFile());
                    primaryProfile = mappingProfile.getName();
                }

                Mapping mapping = mapper.readValue(getClass().getClassLoader().getResource(mappingProfile.getFile()), Mapping.class);
                HashMap<String, Entity> entityHashMap = new HashMap<>();
                for(MappingEntity entity : mapping.getEntities()) {
                    Entity schemaEntity = new Entity(entity.getName());
                    for(MappingField field : entity.getFields()) {
                        schemaEntity.addField(new EntityField(field.getName(), field.getDatatype()));
                    }
                    for(String relation : entity.getRelations()) {
                        schemaEntity.addRelation(relation);
                    }
                    entityHashMap.put(entity.getName(), schemaEntity);
                }
                entities.put(mappingProfile.getName(), entityHashMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initMapper(String mappingFile) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Mapping mapping = mapper.readValue(getClass().getClassLoader().getResource(mappingFile), Mapping.class);
            for(MappingEntity entity : mapping.getEntities()) {
                if(entity.getFilters() != null) {
                    List<Filter> filters = new ArrayList<>();
                    for (MappingFilter filter : entity.getFilters()) {
                        filters.add(new Filter(filter.getColumn(), filter.getType(), filter.getValues(), filter.getDatatype()));
                    }
                    tables.put(entity.getName(), new Table(entity.getFrom(), entity.getKey(), filters));
                } else {
                    tables.put(entity.getName(), new Table(entity.getFrom(), entity.getKey(), null));
                }

                for(MappingField field : entity.getFields()) {
                    if(field.getSqlTable() != null) {
                        fields.put(entity.getName() + "." + field.getName(), new Field(field.getSqlTable(), field.getColumn(), field.getDatatype()));
                    } else {
                        fields.put(entity.getName() + "." + field.getName(), new Field(entity.getFrom(), field.getColumn(), field.getDatatype()));
                    }
                }
            }

            for(MappingRelation relation : mapping.getRelations()) {
                HashMap<String, List<Join>> joinsMap = new HashMap<>();
                for(MappingJoin join : relation.getJoins()) {
                    List<Join> joins = joinsMap.computeIfAbsent(join.getFrom(), k -> new ArrayList<>());
                    joins.add(new Join(join.getFrom(), join.getFromField(), join.getTo(), join.getToField()));

                    joins = joinsMap.computeIfAbsent(join.getTo(), k -> new ArrayList<>());
                    joins.add(new Join(join.getTo(), join.getToField(), join.getFrom(), join.getFromField()));
                }
                String tempFrom = relation.getFrom();
                List<Join> joinList = new ArrayList<>();
                Set<String> doneTables = new HashSet<>();
                while(true) {
                    List<Join> joins = joinsMap.get(tempFrom);
                    if(joins.size() == 1) {
                        joinList.add(joins.get(0));
                        doneTables.add(tempFrom);
                        tempFrom = joins.get(0).getSecond_table();
                    } else {
                        for(Join join : joins) {
                            if(!doneTables.contains(join.getSecond_table())) {
                                joinList.add(join);
                                doneTables.add(tempFrom);
                                tempFrom = join.getSecond_table();
                            }
                        }
                    }
                    if(tempFrom.equals(relation.getTo())) {
                        break;
                    }
                }
                tempFrom = relation.getTo();
                List<Join> revJoinList = new ArrayList<>();
                doneTables = new HashSet<>();
                while(true) {
                    List<Join> joins = joinsMap.get(tempFrom);
                    if(joins.size() == 1) {
                        revJoinList.add(joins.get(0));
                        doneTables.add(tempFrom);
                        tempFrom = joins.get(0).getSecond_table();
                    } else {
                        for(Join join : joins) {
                            if(!doneTables.contains(join.getSecond_table())) {
                                revJoinList.add(join);
                                doneTables.add(tempFrom);
                                tempFrom = join.getSecond_table();
                            }
                        }
                    }
                    if(tempFrom.equals(relation.getFrom())) {
                        break;
                    }
                }

                relations.put(relation.getFrom() + "." + relation.getTo(), joinList);
                relations.put(relation.getTo() + "." + relation.getFrom(), revJoinList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String map(Query query, List<Object> parameters) {
        return mapTree(mapIntermediate(query), parameters);
    }

    public String mapTree(Query query, List<Object> parameters) {
        QueryTree queryTree = new QueryTree(query.getEntity());

        for(Select select : query.getSelect()) {
            queryTree.addSelect(select);
        }
        for(Filter filter : query.getFilters()) {
            queryTree.addFilter(filter);
        }
        return queryTree.makeQuery(parameters);
    }

    public Query mapIntermediate(Query query) {
        List<Select> selects = query.getSelect();
        List<Select> mappedSelects = new ArrayList<>();
        List<Filter> mappedFilters = new ArrayList<>();
        Set<String> filteredEntities = new HashSet<>();

        Table entityTable = tables.get(query.getEntity());

        int selectCount = 1;
        for(Select select : selects) {
            //System.out.println(select.getField());
            List<String> fldPath = new ArrayList<>(Arrays.asList(select.getField().split("\\.")));
            if(fldPath.size() == 1) {
                mappedSelects.add(new Select(entityTable.getTable() + "." + entityTable.getKey(), select.getAggregate(), selectCount));
            } else {
                //String fieldPath = "";
                String fieldPath = query.getEntity();
                for(int i = 0; i < fldPath.size() - 2; i++) {
                    fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities));
                }
                //mappedSelects.add(new Select(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), select.getAggregate()));
                mappedSelects.add(new Select(mapField(fieldPath, fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), select.getAggregate(), selectCount));
            }
            selectCount++;
        }

        List<Filter> filters = query.getFilters();
        for(Filter filter : filters) {
            List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
            String fieldPath = query.getEntity();
            //String fieldPath = "";
            for(int i = 0; i < fldPath.size() - 2; i++) {
                fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities));
            }
            //mappedFilters.add(new Filter(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValue1(), filter.getValue2()));
            mappedFilters.add(new Filter(mapField(fieldPath,fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValues(), fields.get(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)).getDatatype()));
        }
        return new Query(mappedFilters, mappedSelects, entityTable.getTable());
    }

    private String mapTable(String t, String agg, List<Filter> mappedFilters, Set<String> filteredEntities) {
        Table table = tables.get(t);
        if(table.getFilters() != null && !filteredEntities.contains(t)) {
            for(Filter filter : table.getFilters()) {
                mappedFilters.add(new Filter(agg + mapRelation(agg, table.getTable()) + "." + filter.getField(), filter.getType(), filter.getValues(), filter.getDatatype()));
            }
            filteredEntities.add(t);
        }
        return table.getTable();
    }

    private String mapField(String fldPath, String f) {
        Table table = tables.get(f.substring(0, f.indexOf(".")));
        Field field = fields.get(f);
        /*
        String result = fldPath;
        if(!table.getTable().equals(field.getTable())) {
            result += mapRelation(table.getTable(), field.getTable());
        }
        return result + "." + field.getColumn();
        */
        return fldPath + mapRelation(table.getTable(), field.getTable()) + "." + field.getColumn();
    }
    private String mapField(String f) {
        Table table = tables.get(f.substring(0, f.indexOf(".")));
        Field field = fields.get(f);
        if(table.getTable().equals(field.getTable())) {
            return "." + field.getColumn();
        } else {
            //return mapRelationField(table.getTable(), field.getTable()) + "." + field.getColumn();
            return mapRelation(table.getTable(), field.getTable()) + "." + field.getColumn();
        }
    }

    private String mapRelation(String table1, String table2) {
        //System.out.println(table1 + " - " + table2);
        List<Join> joins = relations.get(table1+"."+table2);
        String result = "";
        if(joins == null) {
            return result;
        }
        for(Join join : joins){
            result += "(" + join.getFirst_field() + ")";
            result += ".(" + join.getSecond_field() + ")";
            result += join.getSecond_table();
        }
        return result;
    }

    public void printMapper() {
        System.out.println("tables");
        for(Map.Entry<String, Table> entry : tables.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue().getTable() + " - " + entry.getValue().getKey());
            if(entry.getValue().getFilters() != null) {
                for (Filter filter : entry.getValue().getFilters()) {
                    System.out.println("\t" + filter.getField() + " - " + filter.getType());
                    for(String filterValue : filter.getValues()) {
                        System.out.print(filterValue + " - ");
                    }
                }
            }
        }

        System.out.println("fields");
        for(Map.Entry<String, Field> entry : fields.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue().getTable() + " - " + entry.getValue().getColumn() + " - " + entry.getValue().getDatatype());
        }

        System.out.println("relations");
        for(Map.Entry<String, List<Join>> entry : relations.entrySet()) {
            System.out.println(entry.getKey());
            for(Join join : entry.getValue()) {
                System.out.println('\t' + join.getFirst_table() + "." + join.getFirst_field() + " = " + join.getSecond_table() + "." + join.getSecond_field());
            }
        }
    }

    public HashMap<String, Table> getTables() {
        return tables;
    }

    public HashMap<String, Field> getFields() {
        return fields;
    }

    public HashMap<String, List<Join>> getRelations() {
        return relations;
    }

    public HashMap<String, Entity> getEntities(String profile) {
        if(profile != null) {
            return entities.get(profile);
        } else {
            return entities.get(primaryProfile);
        }
    }

    public List<Profile> getProfiles() {
        return profiles;
    }
}
