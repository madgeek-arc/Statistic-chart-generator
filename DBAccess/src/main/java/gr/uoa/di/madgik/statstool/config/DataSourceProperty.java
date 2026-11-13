package gr.uoa.di.madgik.statstool.config;

public class DataSourceProperty {

    private String id;
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    /** HikariCP maximum pool size. 0 = use default (10). */
    private int maxPoolSize;
    /** HikariCP connection timeout in ms. 0 = use default (20000). */
    private int connTimeoutMs;
    /** SQL run by HikariCP on every new connection (e.g. "USE <db>"). null/blank = none. */
    private String connectionInitSql;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getConnTimeoutMs() {
        return connTimeoutMs;
    }

    public void setConnTimeoutMs(int connTimeoutMs) {
        this.connTimeoutMs = connTimeoutMs;
    }

    public String getConnectionInitSql() {
        return connectionInitSql;
    }

    public void setConnectionInitSql(String connectionInitSql) {
        this.connectionInitSql = connectionInitSql;
    }
}
