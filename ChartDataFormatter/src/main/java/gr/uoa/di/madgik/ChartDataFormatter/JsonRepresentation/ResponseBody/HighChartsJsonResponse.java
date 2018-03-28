package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;

import java.util.List;

public class HighChartsJsonResponse extends JsonResponse {

    @JsonProperty(value = "series")
    private List<AbsData> dataSeries;

    private List<String> xAxis_categories;

    public HighChartsJsonResponse() {}

    public HighChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
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

}
