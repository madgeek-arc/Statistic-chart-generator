package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;
import gr.uoa.di.madgik.statstool.services.SchemaService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "schema/")
public class SchemaController {

    private SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @RequestMapping(value = "entities")
    public List<String> getEntities() {
        return schemaService.getEntities();
    }

    @RequestMapping(value = "entities/{entity}")
    public SchemaEntity getEntity(@PathVariable(value = "entity") String entity) {
        return schemaService.getEntity(entity);
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
