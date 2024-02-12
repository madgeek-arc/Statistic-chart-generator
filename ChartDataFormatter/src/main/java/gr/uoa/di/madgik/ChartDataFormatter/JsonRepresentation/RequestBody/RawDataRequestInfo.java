package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RawDataRequestInfo {

    @JsonProperty
    private List<RawDataSeriesInfo> series;

    @JsonProperty
    private String orderBy;

    @JsonProperty
    private boolean verbose;


    public RawDataRequestInfo() {
    }

    public RawDataRequestInfo(List<RawDataSeriesInfo> series, String orderBy, boolean verbose) {
        this.series = series;
        this.orderBy = orderBy;
        this.verbose = verbose;
    }

    @JsonIgnore
    public List<Query> getQueries(){
        ArrayList<Query> retList = new ArrayList<>();

        for (RawDataSeriesInfo seriesInfo : series)
            retList.add(seriesInfo.getQuery());

        return retList;
    }

    public List<RawDataSeriesInfo> getSeries() {
        return series;
    }

    public void setSeries(List<RawDataSeriesInfo> series) {
        this.series = series;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }
}
