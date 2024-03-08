package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class RawDataJsonResponse extends JsonResponse {

    @JsonProperty
    private List<List<List<?>>> data;

    private final Logger log = LogManager.getLogger(this.getClass());

    public RawDataJsonResponse(List<List<List<?>>> data) {
        this.data = data;
    }

    public RawDataJsonResponse() {
    }

    @Override
    public String toString() {
        return "RawDataJsonResponse{" +
                "data=" + data +
                '}';
    }

    public List<List<List<?>>> getData() {
        return data;
    }

    public void setData(List<List<List<?>>> data) {
        this.data = data;
    }
}
