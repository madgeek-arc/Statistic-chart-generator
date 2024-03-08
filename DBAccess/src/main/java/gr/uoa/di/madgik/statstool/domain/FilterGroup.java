package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class FilterGroup {
    private List<Filter> groupFilters;
    private String op;

    public FilterGroup() {
    }

    public FilterGroup(List<Filter> groupFilters, String op) {
        this.groupFilters = groupFilters;
        this.op = op;
    }

    public List<Filter> getGroupFilters() {
        return groupFilters;
    }

    public void setGroupFilters(List<Filter> groupFilters) {
        this.groupFilters = groupFilters;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return "FilterGroup{" +
                "groupFilters=" + groupFilters +
                ", op='" + op + '\'' +
                '}';
    }
}
