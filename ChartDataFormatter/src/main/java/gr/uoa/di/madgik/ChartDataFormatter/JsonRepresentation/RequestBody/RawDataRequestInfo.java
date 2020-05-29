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
    private boolean verbose;


    public RawDataRequestInfo() {
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

    public static void main(String[] args) throws JsonProcessingException {
        RawDataRequestInfo requestInfo = new RawDataRequestInfo();
        RawDataSeriesInfo seriesInfo = new RawDataSeriesInfo();

        Query q = new Query();

        q.setName("nananaann");
        q.setParameters(Arrays.asList("asda", "adfsds"));

        seriesInfo.setQuery(q);
        requestInfo.setSeries(Arrays.asList(seriesInfo, seriesInfo));
        requestInfo.setVerbose(true);


        System.out.println(new ObjectMapper().writeValueAsString(requestInfo));
    }
}
