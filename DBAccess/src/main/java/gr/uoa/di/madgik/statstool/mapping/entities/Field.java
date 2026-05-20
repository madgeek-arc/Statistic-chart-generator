package gr.uoa.di.madgik.statstool.mapping.entities;

public class Field {
    private String table;
    private String column;
    private String datatype;
    private String description;

    public Field(String table, String column, String datatype) {
        this(table, column, datatype, null);
    }

    public Field(String table, String column, String datatype, String description) {
        this.table = table;
        this.column = column;
        this.datatype = datatype;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
