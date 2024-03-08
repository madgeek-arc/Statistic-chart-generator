package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class VerboseRawDataResponse extends JsonResponse {

    @JsonProperty("datasets")
    private List<VerboseRawDataSeries> series;

    Logger log = LogManager.getLogger(this.getClass());

    public VerboseRawDataResponse() {
    }

    public VerboseRawDataResponse(List<VerboseRawDataSeries> series) {
        this.series = series;
    }

    public List<VerboseRawDataSeries> getSeries() {
        return series;
    }

    public void setSeries(List<VerboseRawDataSeries> series) {
        this.series = series;
    }

    @Override
    public String toString() {
        return "VerboseRawDataResponse{" +
                "series=" + series +
                '}';
    }
}


