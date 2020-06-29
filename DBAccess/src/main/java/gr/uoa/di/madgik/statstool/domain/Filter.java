package gr.uoa.di.madgik.statstool.domain;

import java.util.*;

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

    @Override
    public String toString() {
        return "Filter{" +
                "field='" + field + '\'' +
                ", type='" + type + '\'' +
                ", values=" + values +
                ", datatype='" + datatype + '\'' +
                '}';
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

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!Filter.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final Filter other = (Filter) obj;
        if ((this.field == null) ? (other.field != null) : !this.field.equals(other.field)) {
            return false;
        }
        if ((this.type == null) ? (other.type != null) : !this.type.equals(other.type)) {
            return false;
        }
        if ((this.datatype == null) ? (other.datatype != null) : !this.datatype.equals(other.datatype)) {
            return false;
        }
        if ((this.values == null) ? (other.values != null) : !this.values.equals(other.values)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, type, datatype);
    }
}
