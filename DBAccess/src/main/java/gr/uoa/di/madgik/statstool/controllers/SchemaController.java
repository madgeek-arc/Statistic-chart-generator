package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.mapping.domain.MappingProfile;
import gr.uoa.di.madgik.statstool.mapping.domain.Profile;
import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;
import gr.uoa.di.madgik.statstool.services.SchemaService;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "schema/")
@CrossOrigin(methods = RequestMethod.GET, origins = "*")
public class SchemaController {

    private SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @RequestMapping(value = "profiles")
    public List<Profile> getMappings() {
        return schemaService.getProfiles();
    }

    @RequestMapping(value = "{profile}/entities")
    public List<String> getMappingEntities(@PathVariable(value = "profile") String profile) {
        return schemaService.getEntities(profile);
    }

    @RequestMapping(value = "{profile}/entities/{entity}")
    public SchemaEntity getMappingEntity(@PathVariable(value = "profile") String profile, @PathVariable(value = "entity") String entity) {
        return schemaService.getEntity(profile, entity);
    }

    @RequestMapping(value = "entities")
    public List<String> getEntities() {
        return schemaService.getEntities(null);
    }

    @RequestMapping(value = "entities/{entity}")
    public SchemaEntity getEntity(@PathVariable(value = "entity") String entity) {
        return schemaService.getEntity(null, entity);
    }

    @RequestMapping(value = "fields/{field}")
    public FieldValues getField(@PathVariable(value = "field") String field) {
        return schemaService.getFieldValues(field, "");
    }

    @RequestMapping(value = "fields/{field}/{like}")
    public FieldValues getFieldLike(@PathVariable(value = "field") String field, @PathVariable(value = "like") String like) {
        return schemaService.getFieldValues(field, like);
    }
}
