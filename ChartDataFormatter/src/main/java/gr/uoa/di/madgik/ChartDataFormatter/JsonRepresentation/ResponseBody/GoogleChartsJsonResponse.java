package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import java.util.List;

public class GoogleChartsJsonResponse extends JsonResponse{

    private List<List<Object>> dataTable;

    public GoogleChartsJsonResponse() { }

    public GoogleChartsJsonResponse(List<List<Object>> dataTable) { this.dataTable = dataTable; }

    public List<List<Object>> getDataTable() { return dataTable; }

    public void setDataTable(List<List<Object>> dataTable) { this.dataTable = dataTable; }
}
