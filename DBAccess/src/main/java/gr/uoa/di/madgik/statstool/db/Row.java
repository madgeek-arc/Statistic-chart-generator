package gr.uoa.di.madgik.statstool.db;

import java.util.List;

public class Row {
    private List<String> row;

    public Row() { }
    public Row(List<String> row) {
        this.row = row;
    }

    public List<String> getRow() {
        return row;
    }

    public void setRow(List<String> row) {
        this.row = row;
    }
}
