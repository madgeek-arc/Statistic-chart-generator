package gr.uoa.di.madgik.ChartDataFormatter.nl;

import java.util.List;

public class SqlResult {
    private final String sql;
    private final List<Object> parameters;
    private final String description;

    public SqlResult(String sql, List<Object> parameters) {
        this(sql, parameters, "");
    }

    public SqlResult(String sql, List<Object> parameters, String description) {
        this.sql = sql;
        this.parameters = parameters;
        this.description = description != null ? description : "";
    }

    public String getSql() { return sql; }
    public List<Object> getParameters() { return parameters; }
    public String getDescription() { return description; }
}
