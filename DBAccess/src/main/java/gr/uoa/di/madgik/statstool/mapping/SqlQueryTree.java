package gr.uoa.di.madgik.statstool.mapping;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Select;

import java.util.*;

public class SqlQueryTree {
    private final Node root;
    private int count;
    private final int limit;
    private final List<FilterGroup> filterGroups = new ArrayList<>();

    public SqlQueryTree(Query query) {
        this.root = new Node();
        this.root.table = query.getEntity();
        this.root.alias = query.getEntity().charAt(0) + Integer.toString(this.count);
        this.count++;
        for (Select select : query.getSelect()) {
            addSelect(select);
        }
        for (FilterGroup filterGroup: query.getFilters()) {
            List<Filter> filters = new ArrayList<>();
            // Do NOT mutate the join tree with filter paths; keep filters as-is for EXISTS subqueries
            for (Filter filter : filterGroup.getGroupFilters()) {
                filters.add(filter);
            }
            filterGroups.add(new FilterGroup(filters, filterGroup.getOp()));
        }
        this.limit = query.getLimit();
    }

    private static class Node {
        String table;
        String alias;
        // Parent linkage to reconstruct path for subqueries
        Node parent;
        String parentJoinFrom; // column in parent used in join
        String parentJoinTo;   // column in this node used in join
        //final List<Filter> filters = new ArrayList<>();
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
            parent.alias = fromTable.charAt(0) + Integer.toString(this.count);
            this.count++;
        }

        Edge toEdge = parent.children.get(toTable);
        if (toEdge == null) {
            Node toNode = new Node();
            toNode.table = toTable;
            toNode.alias = toTable.charAt(0) + Integer.toString(this.count);
            this.count++;
            // link parent and join columns for backtracking
            toNode.parent = parent;
            toNode.parentJoinFrom = fromField;
            toNode.parentJoinTo = toField;
            toEdge = new Edge(fromField, toField, toNode);
            parent.children.put(toTable, toEdge);
        }
        return toEdge.node;
    }

    private Filter addFilter(Filter filter) {
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
        return new Filter(parent.alias + "." + field, filter.getType(), filter.getValues(), filter.getDatatype());
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

    public String makeQuery(List<Object> parameters, String orderBy) {
        // Build projected select expressions, using JOINs to derived tables for non-root fields (Impala compatible)
        Stack<Node> stack = new Stack<>();
        List<String> seen = new ArrayList<>();
        List<OrderedSelect> selects = new ArrayList<>();
        List<String> group = new ArrayList<>();
        // Collect non-root selects per node to build derived subqueries
        Map<Node, List<Select>> nonRootSelects = new LinkedHashMap<>();
        // Map select order -> outer expression placeholder to preserve original ordering
        Map<Integer, String> outerExprByOrder = new HashMap<>();

        stack.push(this.root);
        while (!stack.empty()) {
            Node nd = stack.pop();
            if (!seen.contains(nd.alias)) {
                for (Select select : nd.selects) {
                    if (nd == this.root) {
                        // root field/aggregation
                        String expr = select.getField();
                        if (select.getAggregate() == null) {
                            selects.add(new OrderedSelect(select.getOrder(), expr));
                            group.add(expr);
                        } else {
                            String agg = select.getAggregate();
                            if ("count".equals(agg)) {
                                selects.add(new OrderedSelect(select.getOrder(), "COUNT(DISTINCT " + expr + ")"));
                            } else {
                                selects.add(new OrderedSelect(select.getOrder(), agg + "(" + expr + ")"));
                            }
                        }
                    } else {
                        // Defer non-root fields to derived subqueries
                        nonRootSelects.computeIfAbsent(nd, k -> new ArrayList<>()).add(select);
                    }
                }
                seen.add(nd.alias);
            }
            for (Map.Entry<String, Edge> entry : nd.children.entrySet()) {
                if (!seen.contains(entry.getValue().node.alias)) {
                    stack.push(entry.getValue().node);
                }
            }
        }

        // Build derived subqueries and map their columns to select orders
        StringBuilder joins = new StringBuilder();
        int derivedIdx = 0;
        for (Map.Entry<Node, List<Select>> e : nonRootSelects.entrySet()) {
            Node nd = e.getKey();
            List<Select> sels = e.getValue();
            // Reconstruct path from this node back to root
            List<Node> path = new ArrayList<>();
            Node cur = nd;
            while (cur != null && cur != this.root) {
                path.add(cur);
                cur = cur.parent;
            }
            Collections.reverse(path);
            if (path.isEmpty()) continue; // safety

            Node firstNode = path.get(0);
            String firstAlias = "t" + firstNode.alias.charAt(0);
            String dAlias = "d" + (++derivedIdx);

            StringBuilder sub = new StringBuilder();
            sub.append("SELECT ");
            // key on child side
            sub.append(firstAlias).append(".").append(firstNode.parentJoinTo).append(" AS k");

            for (Select s : sels) {
                String targetCol = s.getField().substring(s.getField().indexOf(".") + 1);
                String agg = s.getAggregate();
                String usedAgg = (agg == null) ? "MIN" : ("count".equals(agg) ? "COUNT(DISTINCT" : agg.toUpperCase());
                String tAliasForNode = "t" + nd.alias.charAt(0);
                sub.append(", ");
                if (agg == null) {
                    sub.append(usedAgg).append("(").append(tAliasForNode).append(".").append(targetCol).append(")");
                } else if ("count".equals(agg)) {
                    sub.append("COUNT(DISTINCT ").append(tAliasForNode).append(".").append(targetCol).append(")");
                } else {
                    sub.append(usedAgg).append("(").append(tAliasForNode).append(".").append(targetCol).append(")");
                }
                sub.append(" AS c").append(s.getOrder());
                // map outer expression for this select order
                outerExprByOrder.put(s.getOrder(), dAlias + ".c" + s.getOrder());
            }

            // FROM and JOIN chain inside subquery
            sub.append(" FROM ").append(firstNode.table).append(" ").append(firstAlias).append(" ");
            String prevAlias = firstAlias;
            for (int i = 1; i < path.size(); i++) {
                Node node = path.get(i);
                String curAlias2 = "t" + node.alias.charAt(0);
                sub.append("JOIN ").append(node.table).append(" ").append(curAlias2).append(" ON ")
                   .append(prevAlias).append(".").append(node.parentJoinFrom)
                   .append("=")
                   .append(curAlias2).append(".").append(node.parentJoinTo).append(" ");
                prevAlias = curAlias2;
            }
            sub.append(" GROUP BY ").append(firstAlias).append(".").append(firstNode.parentJoinTo);

            // LEFT JOIN derived subquery to root
            String rootAlias = this.root.alias;
            String on = rootAlias + "." + firstNode.parentJoinFrom + "=" + dAlias + ".k";
            joins.append(" LEFT JOIN (").append(sub).append(") ").append(dAlias).append(" ON ").append(on).append(" ");
        }

        // Prepare final SELECT list preserving original order
        if (!outerExprByOrder.isEmpty()) {
            for (Map.Entry<Integer, String> ent : outerExprByOrder.entrySet()) {
                selects.add(new OrderedSelect(ent.getKey(), ent.getValue()));
            }
        }

        StringBuilder query = new StringBuilder("SELECT ");
        boolean first = true;
        selects.sort(Comparator.comparingInt(o -> o.order));
        for (OrderedSelect select : selects) {
            if (first) {
                query.append(select.select);
                first = false;
            } else {
                query.append(", ").append(select.select);
            }
        }
        // Main FROM on root table only, then join deriveds
        query.append(" FROM ").append(this.root.table).append(" ").append(this.root.alias).append(" ");
        query.append(joins);

        List<String> op = new ArrayList<>();
        List<List<String>> allTheFilters = mapFilters(filterGroups, parameters, op);
        if (!allTheFilters.isEmpty()) {
            query.append("WHERE ");
            first = true;
            int group_id = 0;
            for (List<String> filterGroup : allTheFilters) {
                if (first) {
                    first = false;
                } else {
                    query.append(" AND ");
                }
                boolean first_filter = true;
                for (String filter : filterGroup) {
                    if (first_filter && filterGroup.size() > 1) {
                        query.append("(");
                        first_filter = false;
                    } else if (filterGroup.size() > 1) {
                        query.append(" ").append(op.get(group_id)).append(" ");
                    }
                    query.append(filter);
                }
                if (filterGroup.size() > 1) {
                    query.append(")");
                }
                group_id++;
            }
        }
        if(!group.isEmpty()) {
            query.append(" GROUP BY ");
            first = true;
            for (String gp : group) {
                if (first) {
                    query.append(gp);
                    first = false;
                } else {
                    query.append(", ").append(gp);
                }
            }

            query.append(" ORDER BY ");

            if (orderBy == null || orderBy.equals("xaxis")) {
                first = true;
                for (String gp : group) {
                    if (first) {
                        query.append(gp);
                        first = false;
                    } else {
                        query.append(", ").append(gp);
                    }
                }
            } else {
                query.append(" 1 DESC ");
            }
        }

        if(limit != 0) {
            query.append(" LIMIT ").append(limit);
        }
        query.append(";");

        return query.toString();
    }

    private List<List<String>> mapFilters(List<FilterGroup> filterGroups, List<Object> parameters, List<String> op) {
        List<List<String>> mappedFilters = new ArrayList<>();
        for (FilterGroup filterGroup : filterGroups) {
            List<String> groupFilters = new ArrayList<>();

            // Helper to build predicate for a qualified column and bind params
            java.util.function.BiFunction<String, Filter, String> buildPredicate = (qualifiedCol, f) -> {
                switch (f.getType()) {
                    case "=":
                    case "!=":
                    case ">":
                    case ">=":
                    case "<":
                    case "<=":
                        for (String value : f.getValues()) {
                            parameters.add(mapType(value, f.getDatatype()));
                            return qualifiedCol + f.getType() + "?";
                        }
                        break;
                    case "between":
                        parameters.add(mapType(f.getValues().get(0), f.getDatatype()));
                        parameters.add(mapType(f.getValues().get(1), f.getDatatype()));
                        return qualifiedCol + " BETWEEN ? AND ?";
                    case "contains":
                        for (String value : f.getValues()) {
                            parameters.add(mapType(value.toLowerCase(), f.getDatatype()));
                            return "lower(" + qualifiedCol + ") LIKE CONCAT('%', ?, '%')";
                        }
                        break;
                    case "starts_with":
                        for (String value : f.getValues()) {
                            parameters.add(mapType(value.toLowerCase(), f.getDatatype()));
                            return "lower(" + qualifiedCol + ") LIKE CONCAT(?, '%')";
                        }
                        break;
                    case "ends_with":
                        for (String value : f.getValues()) {
                            parameters.add(mapType(value.toLowerCase(), f.getDatatype()));
                            return "lower(" + qualifiedCol + ") LIKE CONCAT('%', ?)";
                        }
                        break;
                }
                return null;
            };

            if ("OR".equalsIgnoreCase(filterGroup.getOp())) {
                // Rewrite OR of (possibly) subqueries into one EXISTS with UNION ALL of rid values
                List<String> unionBranches = new ArrayList<>();
                String correlationField = null; // r0.<correlationField>

                for (Filter filter : filterGroup.getGroupFilters()) {
                    // Parse the encoded path
                    List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
                    class Hop { String fromTable; String fromField; String toTable; String toField; }
                    List<Hop> hops = new ArrayList<>();
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
                        Hop h = new Hop();
                        h.fromTable = from.substring(0, from.indexOf("("));
                        h.fromField = from.substring(from.indexOf("(") + 1, from.indexOf(")"));
                        h.toTable = to.substring(to.indexOf(")") + 1);
                        h.toField = to.substring(1, to.indexOf(")"));
                        hops.add(h);
                    }
                    String targetColumn = fldPath.get(fldPath.size() - 1);

                    StringBuilder branch = new StringBuilder();
                    if (hops.isEmpty()) {
                        // Root-level: select root ids from the root table applying predicate
                        String rootKey = "id"; // default key
                        String alias = "t0";
                        String qualifiedCol = alias + "." + targetColumn;
                        String pred = buildPredicate.apply(qualifiedCol, filter);
                        if (pred != null) {
                            branch.append("SELECT ").append(alias).append(".").append(rootKey)
                                  .append(" AS rid FROM ").append(this.root.table).append(" ").append(alias)
                                  .append(" WHERE ").append(pred);
                            unionBranches.add(branch.toString());
                        }
                    } else {
                        Hop h0 = hops.get(0);
                        if (correlationField == null) correlationField = h0.fromField;
                        String lastAlias = "s" + (hops.size() - 1);
                        // Build FROM/JOIN chain starting with s0 = first toTable
                        branch.append("SELECT s0.").append(h0.toField).append(" AS rid FROM ")
                              .append(h0.toTable).append(" s0 ");
                        for (int i = 1; i < hops.size(); i++) {
                            Hop hi = hops.get(i);
                            String prevAlias = "s" + (i - 1);
                            String curAlias = "s" + i;
                            branch.append("JOIN ")
                                  .append(hi.toTable).append(" ").append(curAlias)
                                  .append(" ON ")
                                  .append(prevAlias).append(".").append(hi.fromField)
                                  .append("=")
                                  .append(curAlias).append(".").append(hi.toField)
                                  .append(" ");
                        }
                        String qualifiedCol = lastAlias + "." + targetColumn;
                        String pred = buildPredicate.apply(qualifiedCol, filter);
                        if (pred != null) {
                            branch.append("WHERE ").append(pred);
                            unionBranches.add(branch.toString());
                        }
                    }
                }

                if (!unionBranches.isEmpty()) {
                    String corrField = (correlationField != null) ? correlationField : "id";
                    String exists = "EXISTS (SELECT 1 FROM (" + String.join(" UNION ALL ", unionBranches) + ") u WHERE u.rid = " + this.root.alias + "." + corrField + ")";
                    groupFilters.add(exists);
                }
            } else {
                // Default behavior (mostly AND): build simple predicates or EXISTS per filter
                for (Filter filter : filterGroup.getGroupFilters()) {
                    // Parse the path produced by SqlQueryBuilder.mapField(), which encodes joins as: FromTable(from_col).(to_col)ToTable....target_col
                    List<String> fldPath = new ArrayList<>(Arrays.asList(filter.getField().split("\\.")));
                    // Build hop list
                    class Hop { String fromTable; String fromField; String toTable; String toField; }
                    List<Hop> hops = new ArrayList<>();
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
                        Hop h = new Hop();
                        h.fromTable = from.substring(0, from.indexOf("("));
                        h.fromField = from.substring(from.indexOf("(") + 1, from.indexOf(")"));
                        h.toTable = to.substring(to.indexOf(")") + 1);
                        h.toField = to.substring(1, to.indexOf(")"));
                        hops.add(h);
                    }
                    String targetColumn = fldPath.get(fldPath.size() - 1);

                    if (hops.isEmpty()) {
                        // Root-level field: simple predicate on root alias
                        String qualifiedCol = this.root.alias + "." + targetColumn;
                        String predicate = buildPredicate.apply(qualifiedCol, filter);
                        if (predicate != null) groupFilters.add(predicate);
                    } else {
                        // Build EXISTS subquery with its own aliases
                        StringBuilder exists = new StringBuilder();
                        exists.append("EXISTS (SELECT 1 FROM ");
                        String firstAlias = "s0";
                        // First hop determines the starting table in subquery
                        Hop h0 = hops.get(0);
                        exists.append(h0.toTable).append(" ").append(firstAlias).append(" ");
                        for (int i = 1; i < hops.size(); i++) {
                            Hop hi = hops.get(i);
                            String prevAlias = "s" + (i - 1);
                            String curAlias = "s" + i;
                            exists.append("JOIN ")
                                  .append(hi.toTable).append(" ").append(curAlias)
                                  .append(" ON ")
                                  .append(prevAlias).append(".").append(hi.fromField)
                                  .append("=")
                                  .append(curAlias).append(".").append(hi.toField)
                                  .append(" ");
                        }
                        // WHERE correlation to root
                        exists.append("WHERE ")
                              .append(this.root.alias).append(".").append(h0.fromField)
                              .append("=")
                              .append(firstAlias).append(".").append(h0.toField);
                        // Target predicate on the last alias
                        String lastAlias = "s" + (hops.size() - 1);
                        String qualifiedCol = lastAlias + "." + targetColumn;
                        String pred = buildPredicate.apply(qualifiedCol, filter);
                        if (pred != null) {
                            exists.append(" AND ").append(pred);
                        }
                        exists.append(")");
                        groupFilters.add(exists.toString());
                    }
                }
            }

            if (!groupFilters.isEmpty()) {
                mappedFilters.add(groupFilters);
                op.add(filterGroup.getOp());
            }
        }
        return mappedFilters;
    }

    private Object mapType(String value, String datatype) {
        switch (datatype) {
            case "text":
            case "date":
                return value;
            case "int":
                return Integer.parseInt(value);
            case "float":
                return Float.parseFloat(value);
        }
        return null;
    }

}

