package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.mapping.domain.Profile;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;
import gr.uoa.di.madgik.statstool.services.SchemaService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "schema/")
@CrossOrigin(methods = RequestMethod.GET, origins = "*")
public class SchemaController {
    private final Logger log = LogManager.getLogger(this.getClass());

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

    @RequestMapping(value = "{profile}/fields/{field}")
    public FieldValues getMappingField(@PathVariable(value = "profile") String profile, @PathVariable(value = "field") String field) {
        try {
            return schemaService.getFieldValues(profile, field, "");
        } catch (StatsServiceException e) {
            log.error(e);

            return null;
        }
    }

    @RequestMapping(value = "{profile}/fields/{field}/{like}")
    public FieldValues getMappingFieldLike(@PathVariable(value = "profile") String profile, @PathVariable(value = "field") String field, @PathVariable(value = "like") String like) {
        try {
            return schemaService.getFieldValues(profile, field, like);
        } catch (StatsServiceException e) {
            log.error(e);

            return null;
        }
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
        try {
            return schemaService.getFieldValues(null, field, "");
        } catch (StatsServiceException e) {
            log.error(e);

            return null;
        }
    }

    @RequestMapping(value = "fields/{field}/{like}")
    public FieldValues getFieldLike(@PathVariable(value = "field") String field, @PathVariable(value = "like") String like) {
        try {
            return schemaService.getFieldValues(null, field, like);
        } catch (StatsServiceException e) {
            log.error(e);

            return null;
        }
    }
}
