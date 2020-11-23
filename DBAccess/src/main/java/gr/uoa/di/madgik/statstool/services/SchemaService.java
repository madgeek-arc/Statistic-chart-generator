package gr.uoa.di.madgik.statstool.services;

import gr.uoa.di.madgik.statstool.domain.FieldValues;
import gr.uoa.di.madgik.statstool.mapping.domain.Profile;
import gr.uoa.di.madgik.statstool.mapping.entities.SchemaEntity;

import java.util.List;

public interface SchemaService {

    List<Profile> getProfiles();

    List<String> getEntities(String profile);

    SchemaEntity getEntity(String profile, String entity);

    FieldValues getFieldValues(String profile, String field, String like) throws StatsServiceException;
}
