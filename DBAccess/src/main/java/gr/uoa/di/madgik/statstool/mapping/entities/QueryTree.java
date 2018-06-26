package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Select;

public class QueryTree {
    private final Node root;
    private int count;

    public QueryTree(String root) {
        this.root = new Node();
        this.root.table = root;
        this.root.alias = root.substring(0, 1) + Integer.toString(this.count);
        this.count++;
    }

    private class Node {
        String table;
        String alias;
        final List<Filter> filters = new ArrayList<>();
        final List<Select> selects = new ArrayList<>();
        final HashMap<String, Edge> children = new HashMap<>();
    }

    private class Edge {
        final String from;
        final String to;
        final Node node;

        Edge(String from, String to, Node node) {
            this.from = from;
            this.to = to;
            this.node = node;
        }
    }

    private Node addEdge(Node parent, String from, String to) {
        String fromTable = from.substring(0, from.indexOf("("));
        String fromField = from.substring(from.indexOf("(") + 1, from.indexOf(")"));

        String toTable = to.substring(to.indexOf(")") + 1);
        String toField = to.substring(1, to.indexOf(")"));

        if (parent == null) {
            parent = new Node();
            parent.table = fromTable;
            parent.alias = fromTable.substring(0, 1) + Integer.toString(this.count);
            this.count++;
        }

        Edge toEdge = parent.children.get(toTable);
        if (toEdge == null) {
            Node toNode = new Node();
            toNode.table = toTable;
            toNode.alias = toTable.substring(0, 1) + Integer.toString(this.count);
            this.count++;
            toEdge = new Edge(fromField, toField, toNode);
            parent.children.put(toTable, toEdge);
        }
        return toEdge.node;
    }

    public void addFilter(Filter filter) {
        Node parent = this.root;
        List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
        for(int i = 0; i < fldPath.size() - 2; i++) {
            if(i == 0) {
                if(fldPath.size() > 3) {
                    parent = addEdge(parent, fldPath.get(i), fldPath.get(i + 1).substring(0, fldPath.get(i + 1).lastIndexOf("(")));
                } else {
                    parent = addEdge(parent, fldPath.get(i), fldPath.get(i + 1));
                }
            } else if (i != fldPath.size() - 3){
                parent = addEdge(parent, fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
            } else {
                parent = addEdge(parent, fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1));
            }
        }
        String table = fldPath.get(fldPath.size() - 2).substring(fldPath.get(fldPath.size() - 2).indexOf(")") + 1);
        String field = fldPath.get(fldPath.size() - 1);
        parent.filters.add(new Filter(parent.alias + "." + field, filter.getType(), filter.getValues(), filter.getDatatype()));
    }

    public void addSelect(Select select) {
        Node parent = this.root;
        List<String> fldPath = new ArrayList<>(Arrays.asList(select.getField().split("\\.")));
        for(int i = 0; i < fldPath.size() - 2; i++) {
            if(i == 0) {
                if(fldPath.size() > 3) {
                    parent = addEdge(parent, fldPath.get(i), fldPath.get(i + 1).substring(0, fldPath.get(i + 1).lastIndexOf("(")));
                } else {
                    parent = addEdge(parent, fldPath.get(i), fldPath.get(i + 1));
                }
            } else if (i != fldPath.size() - 3){
                parent = addEdge(parent, fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1).substring(0, fldPath.get(i+1).lastIndexOf("(")));
            } else {
                parent = addEdge(parent, fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1), fldPath.get(i+1));
            }
        }
        String table = fldPath.get(fldPath.size() - 2).substring(fldPath.get(fldPath.size() - 2).indexOf(")") + 1);
        String field = fldPath.get(fldPath.size() - 1);
        parent.selects.add(new Select(parent.alias + "." + field, select.getAggregate(), select.getOrder()));
    }

    public String makeQuery(List<Object> parameters) {
        Stack<Node> stack = new Stack<>();
        List<String> tables = new ArrayList<>();
        List<OrderedSelect> selects = new ArrayList<>();
        String joins = "";
        List<Filter> filters = new ArrayList<>();
        List<String> group = new ArrayList<>();

        stack.push(this.root);
        //tables.add(node);
        joins += this.root.table + " " + this.root.alias + " ";

        while (!stack.empty()) {
            Node nd = stack.pop();
            if (!tables.contains(nd.alias)) {
                for (Select select : nd.selects) {
                    if (select.getAggregate() == null) {
                        selects.add(new OrderedSelect(select.getOrder(), select.getField()));
                        group.add(select.getField());
                    } else {
                        if(select.getAggregate().equals("count")) {
                            selects.add(new OrderedSelect(select.getOrder(), select.getAggregate() + "(DISTINCT " + select.getField() + ")"));
                        } else {
                            selects.add(new OrderedSelect(select.getOrder(), select.getAggregate() + "(" + select.getField() + ")"));
                        }
                    }
                }
                filters.addAll(nd.filters);
                tables.add(nd.alias);
            }

            for (Map.Entry<String, Edge> entry : nd.children.entrySet()) {
                //joins.add(nd.table + "." + entry.getValue().from + "=" + entry.getKey() + "." + entry.getValue().to);
                //joins += nd.table + " " + nd.alias + " JOIN " + entry.getKey() + " " + entry.getValue().node.alias + " ON " + nd.alias + "." + entry.getValue().from + "=" + entry.getValue().node.alias + "." + entry.getValue().to + " ";
                joins += "JOIN " + entry.getKey() + " " + entry.getValue().node.alias + " ON " + nd.alias + "." + entry.getValue().from + "=" + entry.getValue().node.alias + "." + entry.getValue().to + " ";
                if (!tables.contains(entry.getValue().node.alias)) {
                    stack.push(entry.getValue().node);
                }
            }
        }

        /*
        System.out.println("tables");
        for(String table : tables) {
            System.out.println(table);
        }
        System.out.println();
        System.out.println("joins");
        for(String join : joins) {
            System.out.println(join);
        }
        System.out.println();
        System.out.println("selects");
        for(String select : selects) {
            System.out.println(select);
        }
        System.out.println();
        System.out.println("filters");
        for(Filter filter : filters) {
            System.out.println(filter.getField() + " - " + filter.getType() + " - " + filter.getValue1() + " - " + filter.getValue2());
        }
        System.out.println();
        System.out.println("group bys");
        for(String gp : group) {
            System.out.println(gp);
        }
        */
        String query = "SELECT ";
        Boolean first = true;
        selects.sort(Comparator.comparingInt(o -> o.order));
        for (OrderedSelect select : selects) {
            if (first) {
                query += select.select;
                first = false;
            } else {
                query += ", " + select.select;
            }
        }
        query += " FROM " + joins;
        /*
        first = true;
        for (String table : tables) {
            if (first) {
                query += table;
                first = false;
            } else {
                query += ", " + table;
            }
        }
        */
        query += "WHERE ";
        first = true;
        /*
        for (String join : joins) {
            if (first) {
                query += join;
                first = false;
            } else {
                query += " AND " + join;
            }
        }
        */
        for (List<String> multipleFilters : mapFilters(filters, parameters)) {
            if (first) {
                first = false;
            } else {
                query += " AND ";
            }
            boolean first_filter = true;
            for(String filter : multipleFilters) {
                if(first_filter && multipleFilters.size() > 1) {
                    query += "(";
                    first_filter = false;
                } else if(multipleFilters.size() > 1) {
                    query += " or ";
                }
                query += filter;
            }
            if(multipleFilters.size() > 1) {
                query += ")";
            }
        }
        query += " GROUP BY ";
        first = true;
        for (String gp : group) {
            if (first) {
                query += gp;
                first = false;
            } else {
                query += ", " + gp;
            }
        }
        query += " ORDER BY ";
        first = true;
        for (String gp : group) {
            if (first) {
                query += gp;
                first = false;
            } else {
                query += ", " + gp;
            }
        }
        query += ";";

        return query;
    }

    private List<List<String>> mapFilters(List<Filter> filters, List<Object> parameters) {
        List<List<String>> mappedFilters = new ArrayList<>();
        for (Filter filter : filters) {
            List<String> multipleFilters = new ArrayList<>();
            if(filter.getType().equals("=") || filter.getType().equals("!=") || filter.getType().equals(">") || filter.getType().equals(">=") || filter.getType().equals("<") || filter.getType().equals("<=")) {
                for(String value: filter.getValues()) {
                    multipleFilters.add(filter.getField() + filter.getType() + "?");
                    parameters.add(mapType(value, filter.getDatatype()));
                }
            } else if (filter.getType().equals("between")) {
                for(int i = 0; i < filter.getValues().size(); i+=2) {
                    multipleFilters.add(filter.getField() + " BETWEEN ? AND ?");
                    parameters.add(mapType(filter.getValues().get(i), filter.getDatatype()));
                    parameters.add(mapType(filter.getValues().get(i+1), filter.getDatatype()));
                }
            } else if(filter.getType().equals("contains")) {
                for(String value: filter.getValues()) {
                    multipleFilters.add("lower(" + filter.getField() + ") LIKE \'%\' || ? || \'%\'");
                    parameters.add(mapType(value.toLowerCase(), filter.getDatatype()));
                }
            } else if(filter.getType().equals("starts_with")) {
                for(String value: filter.getValues()) {
                    multipleFilters.add("lower(" + filter.getField() + ") LIKE ? || \'%\'");
                    parameters.add(mapType(value.toLowerCase(), filter.getDatatype()));
                }
            } else if(filter.getType().equals("ends_with")) {
                for (String value : filter.getValues()) {
                    multipleFilters.add("lower(" + filter.getField() + ") LIKE \'%\' || ?");
                    parameters.add(mapType(value.toLowerCase(), filter.getDatatype()));
                }
            }
            if(!multipleFilters.isEmpty()) {
                mappedFilters.add(multipleFilters);
            }
        }
        return mappedFilters;
    }

    private Object mapType(String value, String datatype) {
        switch (datatype) {
            case "text":
                return value;
            case "int":
                return Integer.parseInt(value);
            case "float":
                return Float.parseFloat(value);
            case "date":
                return value;
        }
        return null;
    }

    private class OrderedSelect {
        final int order;
        final String select;

        OrderedSelect(int order, String select) {
            this.order = order;
            this.select = select;
        }
    }
}

