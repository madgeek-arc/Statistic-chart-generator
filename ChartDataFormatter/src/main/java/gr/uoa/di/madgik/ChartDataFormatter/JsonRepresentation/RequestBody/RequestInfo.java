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

    public RequestInfo() {}

    public RequestInfo(String library, List<ChartInfo> chartsInfo) {
        this.library = library;
        this.chartsInfo = chartsInfo;
    }

    public String getLibrary() {
        return library;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public List<ChartInfo> getChartsInfo() { return chartsInfo; }

    public void setChartsInfo(List<ChartInfo> chartsInfo) { this.chartsInfo = chartsInfo; }

    public List<SupportedChartTypes> getChartTypes(){

        ArrayList<SupportedChartTypes> retList = new ArrayList<>();

        for (ChartInfo chartInfo : chartsInfo)
            try {
                retList.add(SupportedChartTypes.valueOf(chartInfo.getChartType()));
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
}
