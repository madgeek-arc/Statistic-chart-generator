package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;

import java.util.List;

public interface SchemaService {

    List<String> getEntities();

    SchemaEntity getEntity(String entity);
}
