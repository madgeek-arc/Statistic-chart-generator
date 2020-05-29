package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

public class VerboseRawDataSeries {
    private VerboseRawDataSerie series;

    public VerboseRawDataSeries(VerboseRawDataSerie series) {
        this.series = series;
    }

    public VerboseRawDataSeries() {
    }

    public VerboseRawDataSerie getSeries() {
        return series;
    }

    public void setSeries(VerboseRawDataSerie series) {
        this.series = series;
    }
}
