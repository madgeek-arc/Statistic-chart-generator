package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import java.util.List;

public class VerboseRawDataRow {
    List<?> row;

    public VerboseRawDataRow(List<?> row) {
        this.row = row;
    }

    public VerboseRawDataRow() {
    }

    public List<?> getRow() {
        return row;
    }

    public void setRow(List<?> row) {
        this.row = row;
    }
}
