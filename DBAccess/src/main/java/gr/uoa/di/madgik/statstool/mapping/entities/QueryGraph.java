package gr.uoa.di.madgik.statstool.mapping.entities;

import java.util.HashMap;
import java.util.List;

import gr.uoa.di.madgik.statstool.query.Filter;
import gr.uoa.di.madgik.statstool.query.Select;

public class QueryGraph {
    private HashMap<String, Node> nodes;

    private class Node {
        List<Filter> filters;
        List<Select> selects;
        List<Edge> edges;
    }

    private class Edge {
        String from;
        String to;
        Node node;
    }

    public void addEdge(String from, String to) {
        String from_table = from.substring(0, from.indexOf("("));
        String from_field = from.substring(from.indexOf("(") + 1, from.indexOf(")"));

        String to_field = to.substring(to.indexOf(")") + 1);
        String to_table = to.substring(0, to.indexOf("("));

        System.out.println(from_table + " : " + from_field + " - " + to_table + " : " + to_field);
    }
}
