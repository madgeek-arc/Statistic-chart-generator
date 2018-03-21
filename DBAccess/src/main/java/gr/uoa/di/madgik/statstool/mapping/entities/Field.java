package gr.uoa.di.madgik.statstool.mapping.entities;

public class Field {
    private String table;
    private String column;
    private String datatype;

    public Field(String table, String column, String datatype) {
        this.table = table;
        this.column = column;
        this.datatype = datatype;
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
}
