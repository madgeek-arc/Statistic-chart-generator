package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Select;

public class QueryGraph {
    private final HashMap<String, Node> nodes = new HashMap<>();

    private class Node {
        String table;
        final List<Filter> filters = new ArrayList<>();
        final List<Select> selects = new ArrayList<>();
        final HashMap<String, Edge> edges = new HashMap<>();
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

    public void addEdge(String from, String to) {
        String fromTable = from.substring(0, from.indexOf("("));
        String fromField = from.substring(from.indexOf("(") + 1, from.indexOf(")"));

        String toTable = to.substring(to.indexOf(")") + 1);
        String toField = to.substring(1, to.indexOf(")"));

        Node fromNode = nodes.get(fromTable);
        if (fromNode == null) {
            fromNode = new Node();
            fromNode.table = fromTable;
            nodes.put(fromTable, fromNode);
        }

        Node toNode = nodes.get(toTable);
        if (toNode == null) {
            toNode = new Node();
            toNode.table = toTable;
            nodes.put(toTable, toNode);
        }

        if (!fromNode.edges.containsKey(toTable)) {
            fromNode.edges.put(toTable, new Edge(fromField, toField, toNode));
        }

        //System.out.println(fromTable + " : " + fromField + " - " + toTable + " : " + toField);
    }

    public void addFilter(String node, Filter filter) {
        Node nd = nodes.get(node);
        if (nd == null) {
            nd = new Node();
            nd.table = node;
            nodes.put(node, nd);
        }
        nd.filters.add(filter);
    }

    public void addSelect(String node, Select select) {
        Node nd = nodes.get(node);
        if (nd == null) {
            nd = new Node();
            nd.table = node;
            nodes.put(node, nd);
        }
        nd.selects.add(select);
    }

    public String makeQuery(String node, List<Object> parameters) {
        Stack<Node> stack = new Stack<>();
        List<String> tables = new ArrayList<>();
        List<OrderedSelect> selects = new ArrayList<>();
        List<String> joins = new ArrayList<>();
        List<Filter> filters = new ArrayList<>();
        List<String> group = new ArrayList<>();

        stack.push(nodes.get(node));
        //tables.add(node);

        while (!stack.empty()) {
            Node nd = stack.pop();
            if (!tables.contains(nd.table)) {
                for (Select select : nd.selects) {
                    if (select.getAggregate() == null) {
                        selects.add(new OrderedSelect(select.getOrder(), select.getField()));
                        group.add(select.getField());
                    } else {
                        selects.add(new OrderedSelect(select.getOrder(), select.getAggregate() + "(" + select.getField() + ")"));
                    }
                }
                filters.addAll(nd.filters);
                tables.add(nd.table);
            }

            for (Map.Entry<String, Edge> entry : nd.edges.entrySet()) {
                joins.add(nd.table + "." + entry.getValue().from + "=" + entry.getKey() + "." + entry.getValue().to);
                if (!tables.contains(entry.getKey())) {
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
        query += " FROM ";
        first = true;
        for (String table : tables) {
            if (first) {
                query += table;
                first = false;
            } else {
                query += ", " + table;
            }
        }
        query += " WHERE ";
        first = true;
        for (String join : joins) {
            if (first) {
                query += join;
                first = false;
            } else {
                query += " AND " + join;
            }
        }
        for (String filter : mapFilters(filters, parameters)) {
            if (first) {
                query += filter;
                first = false;
            } else {
                query += " AND " + filter;
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

    private List<String> mapFilters(List<Filter> filters, List<Object> parameters) {
        List<String> mappedFilters = new ArrayList<>();
        for (Filter filter : filters) {
            switch (filter.getType()) {
                case "equal":
                    mappedFilters.add(filter.getField() + "='" + filter.getValue1() + "'");
                    //mappedFilters.add(filter.getField() + "=?");
                    //parameters.add(filter.getValue1());
                    break;
                case "between":
                    mappedFilters.add(filter.getField() + ">'" + filter.getValue1() + "'");
                    mappedFilters.add(filter.getField() + "<'" + filter.getValue2() + "'");
                    //mappedFilters.add(filter.getField() + ">?");
                    //mappedFilters.add(filter.getField() + "<?");
                    //parameters.add(filter.getValue1());
                    //parameters.add(filter.getValue2());
                    break;
                case "not_equal":
                    mappedFilters.add(filter.getField() + "!='" + filter.getValue1() + "'");
                    //mappedFilters.add(filter.getField() + "!=?");
                    //parameters.add(filter.getValue1());
                    break;
            }
        }
        return mappedFilters;
    }

    private class OrderedSelect {
        int order;
        String select;

        OrderedSelect(int order, String select) {
            this.order = order;
            this.select = select;
        }
    }
}

