package gr.uoa.di.madgik.ChartDataFormatter.nl;

import java.util.List;

public class SqlResult {
    private final String sql;
    private final List<Object> parameters;

    public SqlResult(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public String getSql() { return sql; }
    public List<Object> getParameters() { return parameters; }
}
