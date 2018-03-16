package gr.uoa.di.madgik.statstool.query;

public class Select {
    private String field;
    private String aggregate;

    public Select() {}

    public Select(String field, String aggregate) {
        this.field = field;
        this.aggregate = aggregate;
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
}
