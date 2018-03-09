package JsonChartRepresentation.HighChartsDataRepresentation;

/**
 * Represents a Tuple of data as shown in the Highcharts <a href="https://api.highcharts.com/highcharts/series.area.data">library</a>
 */
public class DataTuple {

    private Number[] data;

    public DataTuple() {}

    public DataTuple(Number[] data) {
        this.data = data;
    }

    public Number[] getData() {
        return data;
    }

    public void setData(Number[] data) {
        this.data = data;
    }
}
