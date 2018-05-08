package gr.uoa.di.madgik.statstool.controllers;

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

}
