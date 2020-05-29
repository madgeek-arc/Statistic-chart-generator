package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import java.util.List;

public class VerboseRawDataRow {
    List<String> row;

    public VerboseRawDataRow(List<String> row) {
        this.row = row;
    }

    public VerboseRawDataRow() {
    }

    public List<String> getRow() {
        return row;
    }

    public void setRow(List<String> row) {
        this.row = row;
    }
}
