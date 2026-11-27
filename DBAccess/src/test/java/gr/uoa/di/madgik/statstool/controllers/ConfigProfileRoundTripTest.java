package gr.uoa.di.madgik.statstool.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.mapping.domain.Mapping;
import gr.uoa.di.madgik.statstool.mapping.domain.MappingProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** GET /config/profiles/{name} returns the mapping under the same name PUT /config/profiles/{name} reads it from. */
class ConfigProfileRoundTripTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void getBody_isAValidPutBody() throws Exception {
        MappingProfile p = new MappingProfile();
        p.setName("p1");
        p.setDescription("d");
        p.setShareholders(List.of("All"));
        p.setComplexity(2);
        p.setPrimary(true);
        p.setHidden(true);
        p.setContent(JSON.readValue("{\"entities\":[{\"name\":\"result\",\"from\":\"result\",\"key\":\"id\",\"uniqueKey\":true,"
                + "\"fields\":[],\"relations\":[]}],\"relations\":[]}", Mapping.class));

        String body = JSON.writeValueAsString(p);
        JsonNode tree = JSON.readTree(body);
        assertTrue(tree.has("mappingContent"), body);
        assertFalse(tree.has("content"), body);
        assertTrue(tree.at("/mappingContent/entities/0/uniqueKey").asBoolean(), body);

        ConfigController.ProfileRequest req = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(body, ConfigController.ProfileRequest.class);
        MappingProfile back = req.toMappingProfile();
        assertEquals("p1", back.getName());
        assertTrue(back.isPrimary());
        assertTrue(back.isHidden());
        assertEquals(2, back.getComplexity());
        assertEquals(List.of("All"), back.getShareholders());
        assertNotNull(back.getContent());
        assertTrue(back.getContent().getEntities().get(0).isUniqueKey());
        assertEquals(JSON.readTree(JSON.writeValueAsString(p.getContent())), JSON.readTree(JSON.writeValueAsString(back.getContent())));
    }
}
