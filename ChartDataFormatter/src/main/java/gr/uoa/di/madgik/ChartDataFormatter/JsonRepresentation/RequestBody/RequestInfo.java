package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.statstool.domain.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Class holding the necessary information for the Chart Data Formatter to format the query results.
 *
 */
public class RequestInfo {

    @JsonProperty
    private String library;
    @JsonProperty
    private List<ChartInfo> chartsInfo;
    @JsonProperty
    private String orderBy;
    @JsonProperty
    private boolean drilldown;
    @JsonProperty
    private String nl;
    @JsonProperty
    private String sig;
    @JsonProperty
    private String profile;

    public RequestInfo() {}

    public RequestInfo(String library, List<ChartInfo> chartsInfo, String orderBy, boolean drilldown) {
        this.library = library;
        this.chartsInfo = chartsInfo;
        this.orderBy = orderBy;
        this.drilldown = drilldown;
    }

    public String getLibrary() {
        return library;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public List<ChartInfo> getChartsInfo() { return chartsInfo; }

    public void setChartsInfo(List<ChartInfo> chartsInfo) { this.chartsInfo = chartsInfo; }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public boolean isDrilldown() {
        return drilldown;
    }

    public void setDrilldown(boolean drilldown) {
        this.drilldown = drilldown;
    }

    public String getNl() { return nl; }
    public void setNl(String nl) { this.nl = nl; }

    public String getSig() { return sig; }
    public void setSig(String sig) { this.sig = sig; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public boolean isNlRequest() { return nl != null && !nl.isBlank(); }

    public List<SupportedChartTypes> getChartTypes(){

        ArrayList<SupportedChartTypes> retList = new ArrayList<>();

        for (ChartInfo chartInfo : chartsInfo)
            try {
                if(chartInfo.getChartType() != null)
                    retList.add(SupportedChartTypes.valueOf(chartInfo.getChartType()));
                else
                    retList.add(null);
            }catch (IllegalArgumentException e){
                retList.add(null);
            }

        return retList;
    }

    public List<String> getChartNames(){

        ArrayList<String> retList = new ArrayList<>();

        for (ChartInfo chartInfo : chartsInfo)
            retList.add(chartInfo.getChartName());

        return retList;
    }

    public List<Query> getChartQueries(){
        ArrayList<Query> retList = new ArrayList<>();

        for (ChartInfo chartInfo : chartsInfo)
            retList.add(chartInfo.getQuery());

        return retList;
    }

    @Override
    public String toString() {
        return "RequestInfo{" +
                "library='" + library + '\'' +
                ", chartsInfo=" + chartsInfo +
                ", orderBy='" + orderBy + '\'' +
                '}';
    }
}
