package JsonChartRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

/**
 * Represents the <a href="https://api.highcharts.com/highcharts/series">Series</a> field in the Highcharts Documentation.
 * We only care for the data it holds which are passed to the Chartdataizer by the DBAccess
 */
public class DataSeries {

    @JsonProperty(value = "series")
    private ArrayList<AbsData> dataSeries;

    public DataSeries() {}

    public DataSeries(ArrayList<AbsData> data) {

        this.dataSeries = data;
    }

    public ArrayList<AbsData> getDataSeries() {
        return dataSeries;
    }

    public void setDataSeries(ArrayList<AbsData> data) {
        this.dataSeries = data;
    }

}
