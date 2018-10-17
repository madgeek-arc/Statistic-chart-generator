package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import java.util.List;

public class GoogleChartsJsonResponse extends JsonResponse{

    private List<List<Object>> dataTable;
    private List<String> columns;
    private List<String> columnsType;

    public GoogleChartsJsonResponse() { }

    public GoogleChartsJsonResponse(List<List<Object>> dataTable, List<String> columns, List<String> columnsType) {
        this.dataTable = dataTable;
        this.columns = columns;
        this.columnsType = columnsType;
    }

    public List<List<Object>> getDataTable() { return dataTable; }

    public void setDataTable(List<List<Object>> dataTable) { this.dataTable = dataTable; }

    public List<String> getColumns() { return columns; }

    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<String> getColumnsType() { return columnsType; }

    public void setColumnsType(List<String> columnsType) { this.columnsType = columnsType; }
}
