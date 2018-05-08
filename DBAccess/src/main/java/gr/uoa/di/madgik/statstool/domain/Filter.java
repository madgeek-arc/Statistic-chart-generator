package gr.uoa.di.madgik.statstool.domain;

public class Filter {
    private String field;
    private String type;
    private String value1;
    private String value2;
    private String datatype;

    public Filter() {}

    public Filter(String field, String type, String value1, String value2, String datatype) {
        this.field = field;
        this.type = type;
        this.value1 = value1;
        this.value2 = value2;
        this.datatype = datatype;
    }

    /*
    public Filter(String field, String type, String value1, String value2) {
        this.field = field;
        this.type = type;
        this.value1 = value1;
        this.value2 = value2;
    }

    public Filter(String field, String type, String value1) {
        this.field = field;
        this.type = type;
        this.value1 = value1;
    }
    */

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

    public String getValue1() {
        return value1;
    }

    public void setValue1(String value1) {
        this.value1 = value1;
    }

    public String getValue2() {
        return value2;
    }

    public void setValue2(String value2) {
        this.value2 = value2;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }
}
