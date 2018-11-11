package gr.uoa.di.madgik.statstool.mapping.domain;

public class MappingJoin {
    private String from;
    private String fromField;
    private String to;
    private String toField;
    private String array;

    MappingJoin() {}

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromField() {
        return fromField;
    }

    public void setFromField(String fromField) {
        this.fromField = fromField;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getToField() {
        return toField;
    }

    public void setToField(String toField) {
        this.toField = toField;
    }

    public String getArray() {
        return array;
    }

    public void setArray(String array) {
        this.array = array;
    }
}
