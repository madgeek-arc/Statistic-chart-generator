package gr.uoa.di.madgik.statstool.repositories.datasource;

import gr.uoa.di.madgik.statstool.config.DynamicDataSourceRegistry;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

/**
 * Routes each DB call to the correct HikariCP pool via DatasourceContext (ThreadLocal).
 *
 * Overrides determineTargetDataSource() to look up pools from DynamicDataSourceRegistry
 * directly, bypassing AbstractRoutingDataSource's frozen resolved-datasources map so that
 * pools registered after startup are visible immediately.
 */
public class StatsRoutingDatasource extends AbstractRoutingDataSource {

    private final DynamicDataSourceRegistry registry;

    public StatsRoutingDatasource(DynamicDataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return DatasourceContext.getContext();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String key = (String) determineCurrentLookupKey();
        if (key == null) {
            // No context set — JPA bootstrap path or unset caller.
            // hibernate.temp.use_jdbc_metadata_defaults=false prevents Hibernate
            // from probing the DB at startup, so this branch is rarely hit.
            DataSource fallback = registry.getAny();
            if (fallback != null) return fallback;
            throw new IllegalStateException(
                "No datasource context set and registry is empty. " +
                "Ensure DatasourceLoader has run before any DB access.");
        }
        DataSource ds = registry.get(key);
        if (ds == null) {
            throw new IllegalStateException(
                "No datasource registered for key '" + key + "'. " +
                "Check statstool_datasource table or spring.datasources in application.yml.");
        }
        return ds;
    }
}
