package gr.uoa.di.madgik.statstool.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.mapping.domain.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import gr.uoa.di.madgik.statstool.mapping.entities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Query;

import javax.annotation.PostConstruct;

@Component
public class Mapper {

    private final List<Profile> profiles = new ArrayList<>();
    private final HashMap<String, ProfileConfiguration> profileConfigurations = new HashMap<>();
    private final Logger log = LogManager.getLogger(this.getClass());

    private String primaryProfile;
    private ResourceLoader resourceLoader;

    @Autowired
    public Mapper(@Value("${statstool.mappings.file.path}")String mappingsJson, ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        try {
            ObjectMapper mapper = new ObjectMapper();
            MappingProfile[] mappings = mapper.readValue(resourceLoader.getResource(mappingsJson).getURL(), MappingProfile[].class);
            for(MappingProfile mappingProfile : mappings) {
                if(!mappingProfile.isHidden()) {
                    profiles.add(new Profile(mappingProfile.getName(), mappingProfile.getDescription(), mappingProfile.getUsage(), mappingProfile.getShareholders(), mappingProfile.getComplexity()));
                }
                ProfileConfiguration profileConfiguration = new ProfileConfiguration();
                buildConfiguration(mappingProfile.getFile(), profileConfiguration);

                if(mappingProfile.isPrimary()) {
                    primaryProfile = mappingProfile.getName();
                }

                Mapping mapping = mapper.readValue(resourceLoader.getResource(mappingProfile.getFile()).getURL(), Mapping.class);
                for(MappingEntity entity : mapping.getEntities()) {
                    Entity schemaEntity = new Entity(entity.getName());
                    for(MappingField field : entity.getFields()) {
                        schemaEntity.addField(new EntityField(field.getName(), field.getDatatype()));
                    }
                    for(String relation : entity.getRelations()) {
                        schemaEntity.addRelation(relation);
                    }
                    profileConfiguration.entities.put(entity.getName(), schemaEntity);
                }
                profileConfigurations.put(mappingProfile.getName(), profileConfiguration);
            }
        } catch (Exception e) {
            log.error("Error building configuration", e);
        }
    }

    private void buildConfiguration(String mappingFile, ProfileConfiguration profileConfiguration) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Mapping mapping = mapper.readValue(resourceLoader.getResource(mappingFile).getURL(), Mapping.class);
            for(MappingEntity entity : mapping.getEntities()) {
                if(entity.getFilters() != null) {
                    List<Filter> filters = new ArrayList<>();
                    for (MappingFilter filter : entity.getFilters()) {
                        filters.add(new Filter(filter.getColumn(), filter.getType(), filter.getValues(), filter.getDatatype()));
                    }
                    profileConfiguration.tables.put(entity.getName(), new Table(entity.getFrom(), entity.getKey(), filters));
                } else {
                    profileConfiguration.tables.put(entity.getName(), new Table(entity.getFrom(), entity.getKey(), null));
                }

                for(MappingField field : entity.getFields()) {
                    if(field.getSqlTable() != null) {
                        profileConfiguration.fields.put(entity.getName() + "." + field.getName(), new Field(field.getSqlTable(), field.getColumn(), field.getDatatype()));
                    } else {
                        profileConfiguration.fields.put(entity.getName() + "." + field.getName(), new Field(entity.getFrom(), field.getColumn(), field.getDatatype()));
                    }
                }
            }

            for(MappingRelation relation : mapping.getRelations()) {
                HashMap<String, List<Join>> joinsMap = new HashMap<>();
                if(relation.getFrom().equals(relation.getTo())) {
                    for (MappingJoin join : relation.getJoins()) {
                        List<Join> joins = joinsMap.computeIfAbsent(join.getFrom(), k -> new ArrayList<>());
                        joins.add(new Join(join.getFrom(), join.getFromField(), join.getTo(), join.getToField()));
                    }
                    String tempFrom = relation.getFrom();
                    List<Join> joinList = new ArrayList<>();
                    Set<String> doneTables = new HashSet<>();
                    while (true) {
                        List<Join> joins = joinsMap.get(tempFrom);
                        if (joins.size() == 1) {
                            joinList.add(joins.get(0));
                            doneTables.add(tempFrom);
                            tempFrom = joins.get(0).getSecond_table();
                        } else {
                            for (Join join : joins) {
                                if (!doneTables.contains(join.getSecond_table())) {
                                    joinList.add(join);
                                    doneTables.add(tempFrom);
                                    tempFrom = join.getSecond_table();
                                }
                            }
                        }
                        if (tempFrom.equals(relation.getTo())) {
                            break;
                        }
                    }
                    profileConfiguration.relations.put(relation.getFrom() + "." + relation.getTo(), joinList);
                } else {
                    for (MappingJoin join : relation.getJoins()) {
                        List<Join> joins = joinsMap.computeIfAbsent(join.getFrom(), k -> new ArrayList<>());
                        joins.add(new Join(join.getFrom(), join.getFromField(), join.getTo(), join.getToField()));

                        joins = joinsMap.computeIfAbsent(join.getTo(), k -> new ArrayList<>());
                        joins.add(new Join(join.getTo(), join.getToField(), join.getFrom(), join.getFromField()));
                    }
                    String tempFrom = relation.getFrom();
                    List<Join> joinList = new ArrayList<>();
                    Set<String> doneTables = new HashSet<>();
                    while (true) {
                        List<Join> joins = joinsMap.get(tempFrom);
                        if (joins.size() == 1) {
                            joinList.add(joins.get(0));
                            doneTables.add(tempFrom);
                            tempFrom = joins.get(0).getSecond_table();
                        } else {
                            for (Join join : joins) {
                                if (!doneTables.contains(join.getSecond_table())) {
                                    joinList.add(join);
                                    doneTables.add(tempFrom);
                                    tempFrom = join.getSecond_table();
                                }
                            }
                        }
                        if (tempFrom.equals(relation.getTo())) {
                            break;
                        }
                    }
                    tempFrom = relation.getTo();
                    List<Join> revJoinList = new ArrayList<>();
                    doneTables = new HashSet<>();
                    while (true) {
                        List<Join> joins = joinsMap.get(tempFrom);
                        if (joins.size() == 1) {
                            revJoinList.add(joins.get(0));
                            doneTables.add(tempFrom);
                            tempFrom = joins.get(0).getSecond_table();
                        } else {
                            for (Join join : joins) {
                                if (!doneTables.contains(join.getSecond_table())) {
                                    revJoinList.add(join);
                                    doneTables.add(tempFrom);
                                    tempFrom = join.getSecond_table();
                                }
                            }
                        }
                        if (tempFrom.equals(relation.getFrom())) {
                            break;
                        }
                    }

                    profileConfiguration.relations.put(relation.getFrom() + "." + relation.getTo(), joinList);
                    profileConfiguration.relations.put(relation.getTo() + "." + relation.getFrom(), revJoinList);
                }
            }
        } catch (Exception e) {
            log.error("Error building configuration", e);
        }
    }

    public String map(Query query, List<Object> parameters, String orderBy) {
        String profile = query.getProfile();
        if (profile == null) {
            profile = primaryProfile;
        }
        return new SqlQueryBuilder(query, profileConfigurations.get(profile)).getSqlQuery(parameters, orderBy);
    }

    public HashMap<String, Field> getFields(String profile) {
        if(profile != null) {
            return profileConfigurations.get(profile).fields;
        } else {
            return profileConfigurations.get(primaryProfile).fields;
        }
    }

    public HashMap<String, Entity> getEntities(String profile) {
        if(profile != null) {
            return profileConfigurations.get(profile).entities;
        } else {
            return profileConfigurations.get(primaryProfile).entities;
        }
    }

    public List<Profile> getProfiles() {
        return profiles;
    }
}
