package gr.uoa.di.madgik.statstool.mapping.domain;

import java.util.List;

public class MappingRelation {
    private String from;
    private String to;
    private List<MappingJoin> joins;

    MappingRelation() {}

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public List<MappingJoin> getJoins() {
        return joins;
    }

    public void setJoins(List<MappingJoin> joins) {
        this.joins = joins;
    }
}
