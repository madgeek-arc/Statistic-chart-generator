package gr.uoa.di.madgik.statstool.mapping.domain;

import java.util.List;

public class MappingFilter {
    private String column;
    private String type;
    private List<String> values;
    private String datatype;

    MappingFilter() {
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
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
