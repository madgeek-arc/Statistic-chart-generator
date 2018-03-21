package gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gr.uoa.di.madgik.ChartDataizer.JsonChartRepresentation.HighChartsDataRepresentation.HighChartsJsonResponse;

import java.io.IOException;

@JsonDeserialize(using=JsonResponseDeserializer.class)
public abstract class JsonResponse {

}

class JsonResponseDeserializer extends JsonDeserializer<JsonResponse>{
    @Override
    public JsonResponse deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {

        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        ObjectNode root = mapper.readTree(p);

        JsonNode dataNode = root.get("series");
        System.out.println("JsonResponseDeserializer | Node to deserialize: " + mapper.writeValueAsString(dataNode));

        if(dataNode != null)
            return mapper.treeToValue(root,HighChartsJsonResponse.class);

        return null;
    }
}