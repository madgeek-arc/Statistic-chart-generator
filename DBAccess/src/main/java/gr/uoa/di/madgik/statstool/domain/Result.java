package gr.uoa.di.madgik.statstool.domain;

import java.util.ArrayList;
import java.util.List;

public class Result {
    private List<List<?>> rows = new ArrayList<>();

    public Result() {
    }

    public List<List<?>> getRows() {
        return rows;
    }

    public void setRows(List<List<?>> rows) {
        this.rows = rows;
    }

    public void addRow(List<?> row) {
        rows.add(row);
    }

    @Override
    public String toString() {
        return "Result{" +
                "rows=" + rows +
                '}';
    }
}
