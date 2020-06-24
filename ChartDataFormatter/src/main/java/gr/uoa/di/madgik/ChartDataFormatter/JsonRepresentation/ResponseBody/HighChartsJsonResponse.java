package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class HighChartsJsonResponse extends JsonResponse {

    @JsonProperty(value = "series")
    private List<AbsData> dataSeries;

    private List<String> xAxis_categories;

    // dataSeriesNames list must be of the same size as dataSeries list
    private List<String> dataSeriesNames;

    // dataSeriesType list must be of the same size as dataSeries list
    private List<String> dataSeriesTypes;

    private List<AbsData> drilldown;

    private final Logger log = Logger.getLogger(this.getClass());

    public HighChartsJsonResponse() {}

    public HighChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = null;
        this.dataSeriesTypes = null;
        this.drilldown = null;
    }

    public HighChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories, List<String> dataSeriesNames, List<String> dataSeriesTypes) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = dataSeriesNames;
        this.dataSeriesTypes = dataSeriesTypes;
        this.drilldown = null;
    }

    @Override
    public String toString() {
        return "HighChartsJsonResponse{" +
                "dataSeries=" + dataSeries +
                ", xAxis_categories=" + xAxis_categories +
                ", dataSeriesNames=" + dataSeriesNames +
                ", dataSeriesTypes=" + dataSeriesTypes +
                ", drilldown=" + drilldown +
                '}';
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