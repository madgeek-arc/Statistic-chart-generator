package gr.uoa.di.madgik.statstool.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicDataSourceRegistryTest {

    private final DynamicDataSourceRegistry registry = new DynamicDataSourceRegistry();

    @AfterEach
    void tearDown() {
        registry.getIds().forEach(registry::deregister);
    }

    private static DataSourceProperty props(String id, String initSql) {
        DataSourceProperty p = new DataSourceProperty();
        p.setId(id);
        p.setUrl("jdbc:arrow-flight-sql://localhost:31337?useEncryption=false");
        p.setDriverClassName("org.apache.arrow.driver.jdbc.ArrowFlightJdbcDriver");
        p.setConnectionInitSql(initSql);
        return p;
    }

    @Test
    void connectionInitSqlIsPassedToPool() {
        registry.register(props("ie_monitor.public", "USE openaire_prod_stats_monitor_ie"));
        HikariDataSource ds = (HikariDataSource) registry.get("ie_monitor.public");
        assertEquals("USE openaire_prod_stats_monitor_ie", ds.getConnectionInitSql());
    }

    @Test
    void blankConnectionInitSqlIsIgnored() {
        registry.register(props("a.public", null));
        registry.register(props("b.public", "  "));
        assertNull(((HikariDataSource) registry.get("a.public")).getConnectionInitSql());
        assertNull(((HikariDataSource) registry.get("b.public")).getConnectionInitSql());
    }
}
