package gr.uoa.di.madgik.statstool.mapping.domain;

public class MappingField {
    private String column;
    private String name;
    private String datatype;
    private String sqlTable;
    private String description;

    MappingField() {}

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }

    public String getSqlTable() {
        return sqlTable;
    }

    public void setSqlTable(String sqlTable) {
        this.sqlTable = sqlTable;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
