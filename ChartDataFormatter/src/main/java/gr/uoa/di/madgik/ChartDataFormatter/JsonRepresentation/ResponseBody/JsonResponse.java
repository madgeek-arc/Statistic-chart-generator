package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;


/**
 * Represents all of the JSON response types to be given as a Response Body.
 * {@link JsonResponseDeserializer} holds the essence of this abstract class.
 */
@JsonDeserialize(using=JsonResponseDeserializer.class)
public abstract class JsonResponse {

    public abstract void logJsonResponse();
}

/**
 * Judges by which extended class will the {@link JsonResponse} be deserialized.
 */
class JsonResponseDeserializer extends JsonDeserializer<JsonResponse>{
    @Override
    public JsonResponse deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {

        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        ObjectNode root = mapper.readTree(p);

        JsonNode dataNode = root.get("series");
        if(dataNode != null && !(dataNode instanceof NullNode)) {
            return mapper.treeToValue(root, HighChartsJsonResponse.class);
        }
        dataNode = root.get("dataTable");
        if(dataNode != null && !(dataNode instanceof NullNode)) {
            return mapper.treeToValue(root, GoogleChartsJsonResponse.class);
        }

        return null;
    }
}