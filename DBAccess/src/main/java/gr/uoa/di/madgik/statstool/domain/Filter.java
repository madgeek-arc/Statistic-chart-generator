package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class Filter {
    private String field;
    private String type;
    private List<String> values;
    private String datatype;

    public Filter() {}

    public Filter(String field, String type, List<String> values, String datatype) {
        this.field = field;
        this.type = type;
        this.values = values;
        this.datatype = datatype;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }
}
