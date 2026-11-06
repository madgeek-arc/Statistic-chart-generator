package gr.uoa.di.madgik.statstool.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of HikariCP pools keyed by datasource ID.
 * Replaces the static map baked into AbstractRoutingDataSource at boot time.
 * Pools can be added, replaced, or closed at any time without a restart.
 */
@Component
public class DynamicDataSourceRegistry {

    private static final int    DEFAULT_POOL_SIZE    = 10;
    private static final int    DEFAULT_CONN_TIMEOUT = 20_000;

    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Logger log = LogManager.getLogger(this.getClass());

    /** Register (or replace) a datasource pool. Closes the old pool if one existed. */
    public void register(DataSourceProperty props) {
        HikariDataSource ds = buildPool(props);
        HikariDataSource old = pools.put(props.getId(), ds);
        if (old != null && !old.isClosed()) {
            old.close();
        }
        log.info("Registered datasource '{}'", props.getId());
    }

    /** Close and remove a datasource pool. No-op if the ID is unknown. */
    public void deregister(String id) {
        HikariDataSource ds = pools.remove(id);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Deregistered datasource '{}'", id);
        }
    }

    /** Retrieve the DataSource for the given ID, or null if not registered. */
    public DataSource get(String id) {
        return pools.get(id);
    }

    /** Return any registered datasource (used as JPA bootstrap fallback). */
    public DataSource getAny() {
        return pools.values().stream().findFirst().orElse(null);
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(pools.keySet());
    }

    private HikariDataSource buildPool(DataSourceProperty props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("pool-" + props.getId());
        ds.setJdbcUrl(props.getUrl());
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        ds.setDriverClassName(props.getDriverClassName());
        ds.setMaximumPoolSize(props.getMaxPoolSize() > 0 ? props.getMaxPoolSize() : DEFAULT_POOL_SIZE);
        ds.setConnectionTimeout(props.getConnTimeoutMs() > 0 ? props.getConnTimeoutMs() : DEFAULT_CONN_TIMEOUT);

        // HiveConnection.isValid() throws "Method not supported" — use a fallback query.
        if (props.getUrl() != null && props.getUrl().startsWith("jdbc:hive2://")) {
            ds.setConnectionTestQuery("SELECT 1");
        }
        return ds;
    }
}
