package gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

@JsonDeserialize(using=AbsDataDeserializer.class)
public abstract class AbsData {

    private Object data;

    public abstract Object getData();
}

class AbsDataDeserializer extends JsonDeserializer<AbsData>{

    @Override
    public AbsData deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {

        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
        ObjectNode root = mapper.readTree(jsonParser);

        JsonNode dataNode = root.get("data");
//        System.out.println("AbsDataDeserializer | Node to deserialize: " + mapper.writeValueAsString(dataNode));

        if (dataNode != null){
            switch (dataNode.get(0).getNodeType()){
                case ARRAY:
                    return mapper.treeToValue(root,ArrayOfArrays.class);
                case NUMBER:
                    return mapper.treeToValue(root,ArrayOfValues.class);
                case OBJECT:
                case POJO:
                    return mapper.treeToValue(root,ArrayOfObjects.class);
                default:
                    throw new JsonProcessingException("Unexpected JsonNodeType"){};
            }
        }
        return null;
    }
}