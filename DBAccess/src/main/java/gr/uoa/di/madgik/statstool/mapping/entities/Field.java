package gr.uoa.di.madgik.statstool.mapping.entities;

public class Field {
    private String table;
    private String column;
    private String datatype;
    private String array;

    public Field(String table, String column, String datatype, String array) {
        this.table = table;
        this.column = column;
        this.datatype = datatype;
        this.array = array;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }

    public String getArray() {
        return array;
    }

    public void setArray(String array) {
        this.array = array;
    }
}
