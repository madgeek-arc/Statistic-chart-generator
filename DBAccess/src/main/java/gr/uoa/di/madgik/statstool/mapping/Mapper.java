package gr.uoa.di.madgik.statstool.mapping;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
import gr.uoa.di.madgik.statstool.mapping.entities.QueryTree;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import gr.uoa.di.madgik.statstool.query.Filter;
import gr.uoa.di.madgik.statstool.query.Query;
import gr.uoa.di.madgik.statstool.query.Select;

public class Mapper {

    private final HashMap<String, Table> tables = new HashMap<>();
    private final HashMap<String, Field> fields = new HashMap<>();
    private final HashMap<String, List<Join>> relations = new HashMap<>();

    public Mapper() {
        try {
            JSONParser jsonParser = new JSONParser();
            //JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(getClass().getClassLoader().getResource("mapping.json").getFile()));
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/mapping.json"))));
            JSONArray jsonEntities = (JSONArray) jsonObject.get("entities");
            for(Object entity : jsonEntities) {
                JSONObject jsonEntity = (JSONObject) entity;
                JSONArray jsonFields = (JSONArray) jsonEntity.get("field");
                String entityName = jsonEntity.get("@name").toString();
                String entityTable = jsonEntity.get("@from").toString();
                String entityKey = jsonEntity.get("@key").toString();
                if(jsonEntity.get("filters") != null) {
                    List<Filter> entityFilters = new ArrayList<>();
                    JSONArray jsonFilters = (JSONArray) jsonEntity.get("filters");
                    for(Object filter : jsonFilters) {
                        JSONObject jsonFilter = (JSONObject) filter;
                        String filterColumn = jsonFilter.get("@column").toString();
                        String filterType = jsonFilter.get("@type").toString();
                        String filterValue1 = jsonFilter.get("@value1").toString();
                        String filterValue2 = null;
                        String filterDatatype = jsonFilter.get("@datatype").toString();
                        if(jsonFilter.get("@value2") != null) {
                            filterValue2 = jsonFilter.get("@value2").toString();
                        }
                        entityFilters.add(new Filter(filterColumn, filterType, filterValue1, filterValue2, filterDatatype));
                    }
                    tables.put(entityName, new Table(entityTable, entityKey, entityFilters));
                } else {
                    tables.put(entityName, new Table(entityTable, entityKey, null));
                }
                for(Object field : jsonFields) {
                    JSONObject jsonField = (JSONObject) field;
                    String fieldName = jsonField.get("@name").toString();
                    String fieldColumn = jsonField.get("@column").toString();
                    String fieldDataType = jsonField.get("@datatype").toString();
                    String fieldTable = null;
                    if(jsonField.get("@sqlTable") != null) {
                        fieldTable = jsonField.get("@sqlTable").toString();
                    }
                    if(fieldTable != null) {
                        fields.put(entityName + "." + fieldName, new Field(fieldTable, fieldColumn, fieldDataType));
                    } else {
                        fields.put(entityName + "." + fieldName, new Field(entityTable, fieldColumn, fieldDataType));
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
                HashMap<String, List<Join>> joinsMap = new HashMap<>();
                for(Object join : jsonJoins) {
                    JSONObject jsonJoin = (JSONObject) join;
                    List<Join> joins = joinsMap.get(jsonJoin.get("@from").toString());
                    if(joins == null) {
                        joinsMap.put(jsonJoin.get("@from").toString(), joins = new ArrayList<>());
                    }
                    joins.add(new Join(jsonJoin.get("@from").toString(), jsonJoin.get("@from_field").toString(), jsonJoin.get("@to").toString(), jsonJoin.get("@to_field").toString()));
                    joins = joinsMap.get(jsonJoin.get("@to").toString());
                    if(joins == null) {
                        joinsMap.put(jsonJoin.get("@to").toString(), joins = new ArrayList<>());
                    }
                    joins.add(new Join(jsonJoin.get("@to").toString(), jsonJoin.get("@to_field").toString(), jsonJoin.get("@from").toString(), jsonJoin.get("@from_field").toString()));
                }
                String tempFrom = from;
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
                    if(tempFrom.equals(to)) {
                        break;
                    }
                }
                tempFrom = to;
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

    public String mapGraph(Query query, List<Object> parameters) {
        QueryGraph queryGraph = new QueryGraph();
        //queryGraph.addEdge("project(id)", "(id)project_results");

        for(Select select : query.getSelect()) {
            List<String> fldPath = new ArrayList<>(Arrays.asList(select.getField().split("\\.")));
            for(int i = 0; i < fldPath.size() - 2; i++) {
                if(i == 0) {
                    //System.out.println(fldPath.get(i) + " - " + fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                    queryGraph.addEdge(fldPath.get(i), fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                } else if (i != fldPath.size() - 3){
                    //System.out.println(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1) + " - " + fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                    queryGraph.addEdge(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                } else {
                    //System.out.println(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1) + " - " + fldPath.get(i+1));
                    queryGraph.addEdge(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1));
                }
            }
            String table = fldPath.get(fldPath.size() - 2).substring(fldPath.get(fldPath.size() - 2).indexOf(")") + 1);
            String field = fldPath.get(fldPath.size() - 1);
            //System.out.println("addSelect: "  + table + "." + field);
            //System.out.println();
            queryGraph.addSelect(table, new Select(table + "." + field, select.getAggregate(), select.getOrder()));
        }

        for(Filter filter : query.getFilters()) {
            List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
            for(int i = 0; i < fldPath.size() - 2; i++) {
                if(i == 0) {
                    //System.out.println(fldPath.get(i) + " - " + fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                    queryGraph.addEdge(fldPath.get(i), fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                } else if (i != fldPath.size() - 3){
                    //System.out.println(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1) + " - " + fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                    queryGraph.addEdge(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
                } else {
                    //System.out.println(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1) + " - " + fldPath.get(i+1));
                    queryGraph.addEdge(fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1));
                }
            }
            String table = fldPath.get(fldPath.size() - 2).substring(fldPath.get(fldPath.size() - 2).indexOf(")") + 1);
            String field = fldPath.get(fldPath.size() - 1);
            //System.out.println("addFilter: "  + table + "." + field);
            //System.out.println();
            queryGraph.addFilter(table, new Filter(table + "." + field, filter.getType(), filter.getValue1(), filter.getValue2(), filter.getDatatype()));
        }

        return queryGraph.makeQuery(query.getEntity(), parameters);
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
            if(fldPath.get(0).equals(query.getEntity())) {
                if(fldPath.size() == 1) {
                    mappedSelects.add(new Select(entityTable.getTable() + "." + entityTable.getKey(), select.getAggregate(), selectCount));
                } else {
                    //mappedSelects.add(new Select(entityTable.getTable() + mapField(select.getField()), select.getAggregate()));
                    mappedSelects.add(new Select(mapField(entityTable.getTable(), select.getField()), select.getAggregate(), selectCount));
                }
            } else {
                String fieldPath = entityTable.getTable();
                fldPath.add(0, query.getEntity());
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
            if(fldPath.get(0).equals(query.getEntity())) {
                //mappedFilters.add(new Filter(entityTable.getTable() + mapField(filter.getField()), filter.getType(), filter.getValue1(), filter.getValue2()));
                mappedFilters.add(new Filter(mapField(entityTable.getTable(), filter.getField()), filter.getType(), filter.getValue1(), filter.getValue2(), fields.get(filter.getField()).getDatatype()));
            } else {
                String fieldPath = entityTable.getTable();
                fldPath.add(0, query.getEntity());
                for(int i = 0; i < fldPath.size() - 2; i++) {
                    fieldPath += mapRelation(mapTable(fldPath.get(i), entityTable.getTable(), mappedFilters,filteredEntities), mapTable(fldPath.get(i+1), entityTable.getTable(), mappedFilters, filteredEntities));
                }
                //mappedFilters.add(new Filter(fieldPath + mapField(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValue1(), filter.getValue2()));
                mappedFilters.add(new Filter(mapField(fieldPath,fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)), filter.getType(), filter.getValue1(), filter.getValue2(), fields.get(fldPath.get(fldPath.size()-2) + "." + fldPath.get(fldPath.size()-1)).getDatatype()));
            }
        }
        return new Query(mappedFilters, mappedSelects, entityTable.getTable());
    }

    private String mapTable(String t, String agg, List<Filter> mappedFilters, Set<String> filteredEntities) {
        Table table = tables.get(t);
        if(table.getFilters() != null && !filteredEntities.contains(t)) {
            for(Filter filter : table.getFilters()) {
                mappedFilters.add(new Filter(agg + mapRelation(agg, table.getTable()) + "." + filter.getField(), filter.getType(), filter.getValue1(), filter.getValue2(), filter.getDatatype()));
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
}
