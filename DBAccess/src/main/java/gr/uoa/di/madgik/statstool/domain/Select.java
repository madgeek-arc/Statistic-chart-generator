package gr.uoa.di.madgik.statstool.domain;

public class Select {
    private String field;
    private String aggregate;
    private int order;

    public Select() {}

    public Select(String field, String aggregate, int order) {
        this.field = field;
        this.aggregate = aggregate;
        this.order = order;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getAggregate() {
        return aggregate;
    }

    public void setAggregate(String aggregate) {
        this.aggregate = aggregate;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
