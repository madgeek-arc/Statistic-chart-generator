package gr.uoa.di.madgik.statstool.domain;

import java.util.ArrayList;
import java.util.List;

public class Result {
    private List<ArrayList<String>> rows = new ArrayList<>();

    public Result() {
    }

    public List<ArrayList<String>> getRows() {
        return rows;
    }

    public void setRows(List<ArrayList<String>> rows) {
        this.rows = rows;
    }

    public void addRow(ArrayList<String> row) {
        rows.add(row);
    }
}
