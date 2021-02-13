package gr.uoa.di.madgik.statstool.repositories.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class StatsRoutingDatasource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DatasourceContext.getContext();
    }
}
