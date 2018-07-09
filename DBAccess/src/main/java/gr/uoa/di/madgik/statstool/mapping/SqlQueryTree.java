package gr.uoa.di.madgik.statstool.mapping;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Select;

import java.util.*;

public class SqlQueryTree {
    private final Node root;
    private int count;

    public SqlQueryTree(Query query) {
        this.root = new Node();
        this.root.table = query.getEntity();
        this.root.alias = query.getEntity().substring(0, 1) + Integer.toString(this.count);
        this.count++;
        for (Select select : query.getSelect()) {
            addSelect(select);
        }
        for (Filter filter : query.getFilters()) {
            addFilter(filter);
        }
    }

    private static class Node {
        String table;
        String alias;
        final List<Filter> filters = new ArrayList<>();
        final List<Select> selects = new ArrayList<>();
        final HashMap<String, Edge> children = new HashMap<>();
    }

    private static class Edge {
        final String from;
        final String to;
        final Node node;

        Edge(String from, String to, Node node) {
            this.from = from;
            this.to = to;
            this.node = node;
        }
    }

    private static class OrderedSelect {
        final int order;
        final String select;

        OrderedSelect(int order, String select) {
            this.order = order;
            this.select = select;
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

    private void addFilter(Filter filter) {
        Node parent = this.root;
        List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
        for (int i = 0; i < fldPath.size() - 2; i++) {
            String from;
            String to;
            if (i == 0) {
                from = fldPath.get(i);
            } else {
                from = fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1);
            }
            if (fldPath.get(i + 1).endsWith(")")) {
                to = fldPath.get(i + 1).substring(0, fldPath.get(i + 1).lastIndexOf("("));
            } else {
                to = fldPath.get(i + 1);
            }
            parent = addEdge(parent, from, to);
        }
        String field = fldPath.get(fldPath.size() - 1);
        parent.filters.add(new Filter(parent.alias + "." + field, filter.getType(), filter.getValues(), filter.getDatatype()));
    }

    private void addSelect(Select select) {
        Node parent = this.root;
        List<String> fldPath = new ArrayList<>(Arrays.asList(select.getField().split("\\.")));
        for (int i = 0; i < fldPath.size() - 2; i++) {
            String from;
            String to;
            if (i == 0) {
                from = fldPath.get(i);
            } else {
                from = fldPath.get(i).substring(fldPath.get(i).indexOf(")") + 1);
            }
            if (fldPath.get(i + 1).endsWith(")")) {
                to = fldPath.get(i + 1).substring(0, fldPath.get(i + 1).lastIndexOf("("));
            } else {
                to = fldPath.get(i + 1);
            }
            parent = addEdge(parent, from, to);
        }
        String field = fldPath.get(fldPath.size() - 1);
        parent.selects.add(new Select(parent.alias + "." + field, select.getAggregate(), select.getOrder()));
    }

    public String makeQuery(List<Object> parameters) {
        Stack<Node> stack = new Stack<>();
        List<String> tables = new ArrayList<>();
        List<OrderedSelect> selects = new ArrayList<>();
        String joins = "";
        Set<Filter> filters = new HashSet<>();
        List<String> group = new ArrayList<>();

        stack.push(this.root);
        joins += this.root.table + " " + this.root.alias + " ";

        while (!stack.empty()) {
            Node nd = stack.pop();
            if (!tables.contains(nd.alias)) {
                for (Select select : nd.selects) {
                    if (select.getAggregate() == null) {
                        selects.add(new OrderedSelect(select.getOrder(), select.getField()));
                        group.add(select.getField());
                    } else {
                        if (select.getAggregate().equals("count")) {
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
                joins += "JOIN " + entry.getKey() + " " + entry.getValue().node.alias + " ON " + nd.alias + "." + entry.getValue().from + "=" + entry.getValue().node.alias + "." + entry.getValue().to + " ";
                if (!tables.contains(entry.getValue().node.alias)) {
                    stack.push(entry.getValue().node);
                }
            }
        }

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
        query += "WHERE ";
        first = true;
        for (List<String> multipleFilters : mapFilters(filters, parameters)) {
            if (first) {
                first = false;
            } else {
                query += " AND ";
            }
            boolean first_filter = true;
            for (String filter : multipleFilters) {
                if (first_filter && multipleFilters.size() > 1) {
                    query += "(";
                    first_filter = false;
                } else if (multipleFilters.size() > 1) {
                    query += " or ";
                }
                query += filter;
            }
            if (multipleFilters.size() > 1) {
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

    private List<List<String>> mapFilters(Set<Filter> filters, List<Object> parameters) {
        List<List<String>> mappedFilters = new ArrayList<>();
        for (Filter filter : filters) {
            List<String> multipleFilters = new ArrayList<>();
            if (filter.getType().equals("=") || filter.getType().equals("!=") || filter.getType().equals(">") || filter.getType().equals(">=") || filter.getType().equals("<") || filter.getType().equals("<=")) {
                for (String value : filter.getValues()) {
                    multipleFilters.add(filter.getField() + filter.getType() + "?");
                    parameters.add(mapType(value, filter.getDatatype()));
                }
            } else if (filter.getType().equals("between")) {
                for (int i = 0; i < filter.getValues().size(); i += 2) {
                    multipleFilters.add(filter.getField() + " BETWEEN ? AND ?");
                    parameters.add(mapType(filter.getValues().get(i), filter.getDatatype()));
                    parameters.add(mapType(filter.getValues().get(i + 1), filter.getDatatype()));
                }
            } else if (filter.getType().equals("contains")) {
                for (String value : filter.getValues()) {
                    multipleFilters.add("lower(" + filter.getField() + ") LIKE \'%\' || ? || \'%\'");
                    parameters.add(mapType(value.toLowerCase(), filter.getDatatype()));
                }
            } else if (filter.getType().equals("starts_with")) {
                for (String value : filter.getValues()) {
                    multipleFilters.add("lower(" + filter.getField() + ") LIKE ? || \'%\'");
                    parameters.add(mapType(value.toLowerCase(), filter.getDatatype()));
                }
            } else if (filter.getType().equals("ends_with")) {
                for (String value : filter.getValues()) {
                    multipleFilters.add("lower(" + filter.getField() + ") LIKE \'%\' || ?");
                    parameters.add(mapType(value.toLowerCase(), filter.getDatatype()));
                }
            }
            if (!multipleFilters.isEmpty()) {
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

}

