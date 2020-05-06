package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class EChartsJsonResponse extends JsonResponse {

    @JsonProperty(value = "series")
    private List<AbsData> dataSeries;

    private List<String> xAxis_categories;

    // dataSeriesNames list must be of the same size as dataSeries list
    private List<String> dataSeriesNames;

    // dataSeriesType list must be of the same size as dataSeries list
    private List<String> dataSeriesTypes;

    private List<AbsData> drilldown;

    private final Logger log = Logger.getLogger(this.getClass());

    public EChartsJsonResponse() {}

    public EChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = null;
        this.dataSeriesTypes = null;
        this.drilldown = null;
    }

    public EChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories, List<String> dataSeriesNames, List<String> dataSeriesTypes) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = dataSeriesNames;
        this.dataSeriesTypes = dataSeriesTypes;
        this.drilldown = null;
    }

    @Override
    public void logJsonResponse() {
        if(log.isInfoEnabled()) {
            if(this.dataSeriesNames != null)
                log.info("DataSeries Names: " + this.dataSeriesNames.toString());
            if(this.dataSeriesTypes != null)
                log.info("DataSeries Types: " + this.dataSeriesTypes.toString());
            if(this.xAxis_categories != null)
                log.info("DataSeries XAxis_Categories: " + this.xAxis_categories.toString());
            if(this.dataSeries != null) {
                log.info("DataSeries: " + this.dataSeries.toString());
                if(this.getDataSeries().size() > 0 )
                    log.info("DataSeries row size: " + ((ArrayList<Number>) this.dataSeries.get(0).getData()).size());
            }
        }
    }

    public List<AbsData> getDataSeries() {
        return dataSeries;
    }

    public void setDataSeries(List<AbsData> dataSeries) {
        this.dataSeries = dataSeries;
    }

    public List<String> getxAxis_categories() {
        return xAxis_categories;
    }

    public void setxAxis_categories(List<String> xAxis_categories) {
        this.xAxis_categories = xAxis_categories;
    }

    public List<String> getDataSeriesNames() { return dataSeriesNames; }

    public void setDataSeriesNames(List<String> dataSeriesNames) { this.dataSeriesNames = dataSeriesNames; }

    public List<String> getDataSeriesTypes() { return dataSeriesTypes; }

    public void setDataSeriesTypes(List<String> dataSeriesType) { this.dataSeriesTypes = dataSeriesType; }

    public List<AbsData> getDrilldown() { return drilldown; }

    public void setDrilldown(List<AbsData> drilldown) { this.drilldown = drilldown; }
}
