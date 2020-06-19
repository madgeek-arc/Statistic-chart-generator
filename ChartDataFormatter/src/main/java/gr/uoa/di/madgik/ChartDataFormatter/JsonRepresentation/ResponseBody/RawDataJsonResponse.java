package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.util.List;

public class RawDataJsonResponse extends JsonResponse {

    @JsonProperty
    private List<List<List<String>>> data;

    private final Logger log = Logger.getLogger(this.getClass());

    public RawDataJsonResponse(List<List<List<String>>> data) {
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

    public List<List<List<String>>> getData() {
        return data;
    }

    public void setData(List<List<List<String>>> data) {
        this.data = data;
    }

    @Override
    public JsonResponse sort(String field) {
        return this;
    }
}
