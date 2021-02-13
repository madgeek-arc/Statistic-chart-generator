package gr.uoa.di.madgik.statstool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "spring")
public class DataSourceProperties {

    private List<DataSourceProperty> dataSources = new ArrayList<>();

    public DataSourceProperties(List<DataSourceProperty> dataSources) {
        this.dataSources = dataSources;
    }

    public List<DataSourceProperty> getDataSources() {
        return dataSources;
    }

    public void setDataSources(List<DataSourceProperty> dataSources) {
        this.dataSources = dataSources;
    }
}