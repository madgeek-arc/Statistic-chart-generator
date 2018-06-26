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

    private final List<Profile> profiles = new ArrayList<>();
    private final HashMap<String, ProfileConfiguration> profileConfigurations = new HashMap<>();

    private String primaryProfile;

    public NewMapper() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            MappingProfile[] mappings = mapper.readValue(getClass().getClassLoader().getResource("mappings.json"), MappingProfile[].class);
            for(MappingProfile mappingProfile : mappings) {
                profiles.add(new Profile(mappingProfile.getName(), mappingProfile.getDescription()));
                ProfileConfiguration profileConfiguration = new ProfileConfiguration();
                buildConfiguration(mappingProfile.getFile(), profileConfiguration);

                if(mappingProfile.isPrimary()) {
                    primaryProfile = mappingProfile.getName();
                }

                Mapping mapping = mapper.readValue(getClass().getClassLoader().getResource(mappingProfile.getFile()), Mapping.class);
                for(MappingEntity entity : mapping.getEntities()) {
                    Entity schemaEntity = new Entity(entity.getName());
                    for(MappingField field : entity.getFields()) {
                        schemaEntity.addField(new EntityField(field.getName(), field.getDatatype()));
                    }
                    for(String relation : entity.getRelations()) {
                        schemaEntity.addRelation(relation);
                    }
                    profileConfiguration.entities.put(entity.getName(), schemaEntity);
                }
                profileConfigurations.put(mappingProfile.getName(), profileConfiguration);
                //entities.put(mappingProfile.getName(), entityHashMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildConfiguration(String mappingFile, ProfileConfiguration profileConfiguration) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Mapping mapping = mapper.readValue(getClass().getClassLoader().getResource(mappingFile), Mapping.class);
            for(MappingEntity entity : mapping.getEntities()) {
                if(entity.getFilters() != null) {
                    List<Filter> filters = new ArrayList<>();
                    for (MappingFilter filter : entity.getFilters()) {
                        filters.add(new Filter(filter.getColumn(), filter.getType(), filter.getValues(), filter.getDatatype()));
                    }
                    profileConfiguration.tables.put(entity.getName(), new Table(entity.getFrom(), entity.getKey(), filters));
                } else {
                    profileConfiguration.tables.put(entity.getName(), new Table(entity.getFrom(), entity.getKey(), null));
                }

                for(MappingField field : entity.getFields()) {
                    if(field.getSqlTable() != null) {
                        profileConfiguration.fields.put(entity.getName() + "." + field.getName(), new Field(field.getSqlTable(), field.getColumn(), field.getDatatype()));
                    } else {
                        profileConfiguration.fields.put(entity.getName() + "." + field.getName(), new Field(entity.getFrom(), field.getColumn(), field.getDatatype()));
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

                profileConfiguration.relations.put(relation.getFrom() + "." + relation.getTo(), joinList);
                profileConfiguration.relations.put(relation.getTo() + "." + relation.getFrom(), revJoinList);
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

        String profile = query.getProfile();
        if(profile == null) {
            profile = primaryProfile;
        }
        ProfileConfiguration profileConfiguration = profileConfigurations.get(profile);

        Table entityTable = profileConfiguration.tables.get(query.getEntity());

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
                    fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities, profileConfiguration), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities, profileConfiguration), profileConfiguration);
                }
                //mappedSelects.add(new Select(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), select.getAggregate()));
                mappedSelects.add(new Select(mapField(fieldPath, fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1), profileConfiguration), select.getAggregate(), selectCount));
            }
            selectCount++;
        }

        List<Filter> filters = query.getFilters();
        for(Filter filter : filters) {
            List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
            String fieldPath = query.getEntity();
            //String fieldPath = "";
            for(int i = 0; i < fldPath.size() - 2; i++) {
                fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities, profileConfiguration), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities, profileConfiguration), profileConfiguration);
            }
            //mappedFilters.add(new Filter(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValue1(), filter.getValue2()));
            mappedFilters.add(new Filter(mapField(fieldPath,fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1), profileConfiguration), filter.getType(), filter.getValues(), profileConfiguration.fields.get(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)).getDatatype()));
        }

        if(entityTable.getFilters() != null && !filteredEntities.contains(query.getEntity())) {
            for(Filter filter : entityTable.getFilters()) {
                mappedFilters.add(new Filter(entityTable.getTable() + "." + filter.getField(), filter.getType(), filter.getValues(), filter.getDatatype()));
            }
            filteredEntities.add(query.getEntity());
        }
        return new Query(mappedFilters, mappedSelects, entityTable.getTable(), query.getProfile());
    }

    private String mapTable(String t, String agg, List<Filter> mappedFilters, Set<String> filteredEntities, ProfileConfiguration profileConfiguration) {
        Table table = profileConfiguration.tables.get(t);
        if(table.getFilters() != null && !filteredEntities.contains(t)) {
            for(Filter filter : table.getFilters()) {
                mappedFilters.add(new Filter(agg + mapRelation(agg, table.getTable(), profileConfiguration) + "." + filter.getField(), filter.getType(), filter.getValues(), filter.getDatatype()));
            }
            filteredEntities.add(t);
        }
        return table.getTable();
    }

    private String mapField(String fldPath, String f, ProfileConfiguration profileConfiguration) {
        Table table = profileConfiguration.tables.get(f.substring(0, f.indexOf(".")));
        Field field = profileConfiguration.fields.get(f);
        /*
        String result = fldPath;
        if(!table.getTable().equals(field.getTable())) {
            result += mapRelation(table.getTable(), field.getTable());
        }
        return result + "." + field.getColumn();
        */
        return fldPath + mapRelation(table.getTable(), field.getTable(), profileConfiguration) + "." + field.getColumn();
    }
    /*
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
    */

    private String mapRelation(String table1, String table2, ProfileConfiguration profileConfiguration) {
        //System.out.println(table1 + " - " + table2);
        List<Join> joins = profileConfiguration.relations.get(table1+"."+table2);
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

    /*
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
    */

    public HashMap<String, Field> getFields(String profile) {
        if(profile != null) {
            return profileConfigurations.get(profile).fields;
        } else {
            return profileConfigurations.get(primaryProfile).fields;
        }
    }

    public HashMap<String, Entity> getEntities(String profile) {
        if(profile != null) {
            return profileConfigurations.get(profile).entities;
        } else {
            return profileConfigurations.get(primaryProfile).entities;
        }
    }

    public List<Profile> getProfiles() {
        return profiles;
    }
}
