package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class GoogleChartsJsonResponse extends JsonResponse{

    private List<List<Object>> dataTable;
    private List<String> columns;
    private List<String> columnsType;

    private final Logger log = Logger.getLogger(this.getClass());

    public GoogleChartsJsonResponse() { }

    public GoogleChartsJsonResponse(List<List<Object>> dataTable, List<String> columns, List<String> columnsType) {
        this.dataTable = dataTable;
        this.columns = columns;
        this.columnsType = columnsType;
    }

    @Override
    public void logJsonResponse() {
        if(log.isInfoEnabled()) {
            if(this.columnsType != null)
                log.info("ColumnsType: " + this.columnsType.toString());
            if(this.columns != null)
                log.info("Columns: " + this.columns.toString());
            if(this.dataTable != null)
                log.info("DataTable :" + this.dataTable.toString());
        }
    }

    public List<List<Object>> getDataTable() { return dataTable; }

    public void setDataTable(List<List<Object>> dataTable) { this.dataTable = dataTable; }

    public List<String> getColumns() { return columns; }

    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<String> getColumnsType() { return columnsType; }

    public void setColumnsType(List<String> columnsType) { this.columnsType = columnsType; }
}
