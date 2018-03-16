package gr.uoa.di.madgik.statstool.mapping.entities;

public class Field {
    private String table;
    private String column;

    public Field(String table, String column) {
        this.table = table;
        this.column = column;
    }

    public String getTable() {
        return table;
    }

    public String getColumn() {
        return column;
    }
}
