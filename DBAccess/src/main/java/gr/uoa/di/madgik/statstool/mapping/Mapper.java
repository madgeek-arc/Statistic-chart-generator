package gr.uoa.di.madgik.statstool.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.Join;
import gr.uoa.di.madgik.statstool.mapping.entities.QueryGraph;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import gr.uoa.di.madgik.statstool.query.Filter;
import gr.uoa.di.madgik.statstool.query.Query;
import gr.uoa.di.madgik.statstool.query.Select;

public class Mapper {

    private HashMap<String, Table> tables = new HashMap<String, Table>();
    private HashMap<String, Field> fields = new HashMap<String, Field>();
    private HashMap<String, List<Join>> relations = new HashMap<String, List<Join>>();

    public Mapper() {
        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(getClass().getClassLoader().getResource("mapping.json").getFile()));
            JSONArray jsonEntities = (JSONArray) jsonObject.get("entities");
            for(Object entity : jsonEntities) {
                JSONObject jsonEntity = (JSONObject) entity;
                JSONArray jsonFields = (JSONArray) jsonEntity.get("field");
                String entityName = jsonEntity.get("@name").toString();
                String entityTable = jsonEntity.get("@from").toString();
                String entityKey = jsonEntity.get("@key").toString();
                if(jsonEntity.get("filters") != null) {
                    List<Filter> entityFilters = new ArrayList<Filter>();
                    JSONArray jsonFilters = (JSONArray) jsonEntity.get("filters");
                    for(Object filter : jsonFilters) {
                        JSONObject jsonFilter = (JSONObject) filter;
                        String filterColumn = jsonFilter.get("@column").toString();
                        String filterType = jsonFilter.get("@type").toString();
                        String filterValue1 = jsonFilter.get("@value1").toString();
                        String filterValue2 = null;
                        if(jsonFilter.get("@value2") != null) {
                            filterValue2 = jsonFilter.get("@value2").toString();
                        }
                        entityFilters.add(new Filter(filterColumn, filterType, filterValue1, filterValue2));
                    }
                    tables.put(entityName, new Table(entityTable, entityKey, entityFilters));
                } else {
                    tables.put(entityName, new Table(entityTable, entityKey, null));
                }
                for(Object field : jsonFields) {
                    JSONObject jsonField = (JSONObject) field;
                    String fieldName = jsonField.get("@name").toString();
                    String fieldColumn = jsonField.get("@column").toString();
                    String fieldTable = null;
                    if(jsonField.get("@sqlTable") != null) {
                        fieldTable = jsonField.get("@sqlTable").toString();
                    }
                    if(fieldTable != null) {
                        fields.put(entityName + "." + fieldName, new Field(fieldTable, fieldColumn));
                    } else {
                        fields.put(entityName + "." + fieldName, new Field(entityTable, fieldColumn));
                    }

                }
            }
            /*
            JSONArray jsonRelations = (JSONArray) jsonObject.get("relations");
            for(Object relation : jsonRelations) {
                JSONObject jsonRelation = (JSONObject) relation;
                String from = jsonRelation.get("@from").toString();
                String to = jsonRelation.get("@to").toString();
                List<Join> joinList = new ArrayList<Join>();
                JSONArray jsonJoins = (JSONArray) jsonRelation.get("join");
                for(Object join : jsonJoins) {
                    JSONObject jsonJoin = (JSONObject) join;
                    joinList.add(new Join(jsonJoin.get("@from").toString(),jsonJoin.get("@from_field").toString(),jsonJoin.get("@to").toString(),jsonJoin.get("@to_field").toString()));
                }
                relations.put(from + "." + to, joinList);
                relations.put(to + "." + from, joinList);
            }
            */
            JSONArray jsonRelations = (JSONArray) jsonObject.get("relations");
            for(Object relation : jsonRelations) {
                JSONObject jsonRelation = (JSONObject) relation;
                String from = jsonRelation.get("@from").toString();
                String to = jsonRelation.get("@to").toString();
                JSONArray jsonJoins = (JSONArray) jsonRelation.get("join");
                HashMap<String, List<Join>> joinsMap = new HashMap<String, List<Join>>();
                for(Object join : jsonJoins) {
                    JSONObject jsonJoin = (JSONObject) join;
                    List<Join> joins = joinsMap.get(jsonJoin.get("@from").toString());
                    if(joins == null) {
                        joinsMap.put(jsonJoin.get("@from").toString(), joins = new ArrayList<Join>());
                    }
                    joins.add(new Join(jsonJoin.get("@from").toString(), jsonJoin.get("@from_field").toString(), jsonJoin.get("@to").toString(), jsonJoin.get("@to_field").toString()));
                    joins = joinsMap.get(jsonJoin.get("@to").toString());
                    if(joins == null) {
                        joinsMap.put(jsonJoin.get("@to").toString(), joins = new ArrayList<Join>());
                    }
                    joins.add(new Join(jsonJoin.get("@to").toString(), jsonJoin.get("@to_field").toString(), jsonJoin.get("@from").toString(), jsonJoin.get("@from_field").toString()));
                }
                String tempFrom = from;
                List<Join> joinList = new ArrayList<Join>();
                Set<String> doneTables = new HashSet<String>();
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
                    if(tempFrom.equals(to)) {
                        break;
                    }
                }
                tempFrom = to;
                List<Join> revJoinList = new ArrayList<Join>();
                doneTables = new HashSet<String>();
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
                    if(tempFrom.equals(from)) {
                        break;
                    }
                }
                /*
                for(Map.Entry<String, List<Join>> entry : joinsMap.entrySet()) {
                    System.out.println(entry.getKey());
                    for(Join join : entry.getValue()) {
                        System.out.println("\t" + join.getFirst_table() + "." + join.getFirst_field() + " = " + join.getSecond_table() + "." + join.getSecond_field());
                    }
                }
                System.out.println();
                */

                relations.put(from + "." + to, joinList);
                relations.put(to + "." + from, revJoinList);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void graph(Query query) {
        QueryGraph queryGraph = new QueryGraph();
        queryGraph.addEdge("project(id)", "(id)project_results");

    }

    public Query map(Query query) {
        List<Select> selects = query.getSelect();
        List<Select> mappedSelects = new ArrayList<Select>();
        List<Filter> mappedFilters = new ArrayList<Filter>();
        Set<String> filteredEntities = new HashSet<String>();

        Table entityTable = tables.get(query.getEntity());

        for(Select select : selects) {
            //System.out.println(select.getField());
            List<String> fldPath = new ArrayList<String>(Arrays.asList(select.getField().split("\\.")));
            if(fldPath.get(0).equals(query.getEntity())) {
                if(fldPath.size() == 1) {
                    mappedSelects.add(new Select(entityTable.getTable() + "." + entityTable.getKey(), select.getAggregate()));
                } else {
                    //mappedSelects.add(new Select(entityTable.getTable() + mapField(select.getField()), select.getAggregate()));
                    mappedSelects.add(new Select(mapField(entityTable.getTable(), select.getField()), select.getAggregate()));
                }
            } else {
                String fieldPath = entityTable.getTable();
                fldPath.add(0, query.getEntity());
                for(int i = 0; i < fldPath.size() - 2; i++) {
                    fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities));
                }
                //mappedSelects.add(new Select(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), select.getAggregate()));
                mappedSelects.add(new Select(mapField(fieldPath, fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), select.getAggregate()));
            }
        }

        List<Filter> filters = query.getFilters();
        for(Filter filter : filters) {
            List<String> fldPath = new ArrayList<String>(Arrays.asList(filter.getField().split("\\.")));
            if(fldPath.get(0).equals(query.getEntity())) {
                //mappedFilters.add(new Filter(entityTable.getTable() + mapField(filter.getField()), filter.getType(), filter.getValue1(), filter.getValue2()));
                mappedFilters.add(new Filter(mapField(entityTable.getTable(), filter.getField()), filter.getType(), filter.getValue1(), filter.getValue2()));
            } else {
                String fieldPath = entityTable.getTable();
                fldPath.add(0, query.getEntity());
                for(int i = 0; i < fldPath.size() - 2; i++) {
                    fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities));
                }
                //mappedFilters.add(new Filter(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValue1(), filter.getValue2()));
                mappedFilters.add(new Filter(mapField(fieldPath,fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValue1(), filter.getValue2()));
            }
        }
        return new Query(mappedFilters, mappedSelects, entityTable.getTable());
    }

    private String mapTable(String t, String agg, List<Filter> mappedFilters, Set<String> filteredEntities) {
        Table table = tables.get(t);
        if(table.getFilters() != null && !filteredEntities.contains(t)) {
            for(Filter filter : table.getFilters()) {
                mappedFilters.add(new Filter(agg + mapRelation(agg, table.getTable()) + "." + filter.getField(), filter.getType(), filter.getValue1(), filter.getValue2()));
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
                    System.out.println("\t" + filter.getField() + " - " + filter.getType() + " - " + filter.getValue1() + " - " + filter.getValue2());
                }
            }
        }

        System.out.println("fields");
        for(Map.Entry<String, Field> entry : fields.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue().getTable() + " - " + entry.getValue().getColumn());
        }

        System.out.println("relations");
        for(Map.Entry<String, List<Join>> entry : relations.entrySet()) {
            System.out.println(entry.getKey());
            for(Join join : entry.getValue()) {
                System.out.println('\t' + join.getFirst_table() + "." + join.getFirst_field() + " = " + join.getSecond_table() + "." + join.getSecond_field());
            }
        }
    }
}
