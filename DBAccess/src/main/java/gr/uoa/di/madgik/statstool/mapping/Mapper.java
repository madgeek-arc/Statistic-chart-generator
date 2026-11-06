package gr.uoa.di.madgik.statstool.mapping;

import gr.uoa.di.madgik.statstool.mapping.domain.*;
import gr.uoa.di.madgik.statstool.mapping.entities.*;
import gr.uoa.di.madgik.statstool.repositories.ConfigRepository;
import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Query;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Mapper {

    private volatile List<Profile> profiles = new ArrayList<>();
    private volatile HashMap<String, ProfileConfiguration> profileConfigurations = new HashMap<>();
    private volatile String primaryProfile;

    private final Logger log = LogManager.getLogger(this.getClass());

    @Autowired
    private ConfigRepository configRepository;

    @PostConstruct
    public void init() {
        reload();
    }

    /** Hot-reload all profiles from the DB. Thread-safe; callers see the new state immediately. */
    public synchronized void reload() {
        List<MappingProfile> mappingProfiles = configRepository.loadProfiles();
        if (mappingProfiles.isEmpty()) {
            log.warn("No mapping profiles found in DB — schema endpoints will return empty results");
        }

        List<Profile> newProfiles = new ArrayList<>();
        HashMap<String, ProfileConfiguration> newConfigs = new HashMap<>();
        String newPrimary = null;

        for (MappingProfile mp : mappingProfiles) {
            if (!mp.isHidden()) {
                newProfiles.add(new Profile(
                    mp.getName(), mp.getDescription(), mp.getUsage(),
                    mp.getShareholders(), mp.getComplexity()));
            }

            if (mp.isPrimary()) {
                newPrimary = mp.getName();
            }

            Mapping mapping = mp.getContent();
            if (mapping == null) {
                log.error("Profile '{}' has null mapping content — skipping", mp.getName());
                continue;
            }

            ProfileConfiguration cfg = new ProfileConfiguration();
            buildConfiguration(mapping, cfg);
            buildEntities(mapping, cfg);
            newConfigs.put(mp.getName(), cfg);
        }

        // Atomic swap — readers see either the old or the new state, never a partial update.
        this.profiles = newProfiles;
        this.profileConfigurations = newConfigs;
        this.primaryProfile = newPrimary;
        log.info("Mapper reloaded: {} profile(s), primary='{}'", newConfigs.size(), newPrimary);
    }

    // -------------------------------------------------------------------------
    // Internal builders (identical logic to the original file-based version)
    // -------------------------------------------------------------------------

    private void buildConfiguration(Mapping mapping, ProfileConfiguration cfg) {
        for (MappingEntity entity : mapping.getEntities()) {
            String entityDesc = entity.getDescription() != null ? entity.getDescription() : entity.getName();
            if (entity.getFilters() != null) {
                List<Filter> filters = new ArrayList<>();
                for (MappingFilter filter : entity.getFilters()) {
                    filters.add(new Filter(filter.getColumn(), filter.getType(),
                        filter.getValues(), filter.getDatatype()));
                }
                cfg.tables.put(entity.getName(),
                    new Table(entity.getFrom(), entity.getKey(), filters, entityDesc));
            } else {
                cfg.tables.put(entity.getName(),
                    new Table(entity.getFrom(), entity.getKey(), null, entityDesc));
            }

            for (MappingField field : entity.getFields()) {
                String fieldDesc = field.getDescription() != null ? field.getDescription() : field.getName();
                String sqlTable = field.getSqlTable() != null ? field.getSqlTable() : entity.getFrom();
                cfg.fields.put(entity.getName() + "." + field.getName(),
                    new Field(sqlTable, field.getColumn(), field.getDatatype(), fieldDesc));
            }
        }

        for (MappingRelation relation : mapping.getRelations()) {
            HashMap<String, List<Join>> joinsMap = new HashMap<>();
            if (relation.getFrom().equals(relation.getTo())) {
                for (MappingJoin join : relation.getJoins()) {
                    joinsMap.computeIfAbsent(join.getFrom(), k -> new ArrayList<>())
                        .add(new Join(join.getFrom(), join.getFromField(),
                            join.getTo(), join.getToField()));
                }
                cfg.relations.put(relation.getFrom() + "." + relation.getTo(),
                    walkPath(relation.getFrom(), relation.getTo(), joinsMap));
            } else {
                for (MappingJoin join : relation.getJoins()) {
                    joinsMap.computeIfAbsent(join.getFrom(), k -> new ArrayList<>())
                        .add(new Join(join.getFrom(), join.getFromField(),
                            join.getTo(), join.getToField()));
                    joinsMap.computeIfAbsent(join.getTo(), k -> new ArrayList<>())
                        .add(new Join(join.getTo(), join.getToField(),
                            join.getFrom(), join.getFromField()));
                }
                cfg.relations.put(relation.getFrom() + "." + relation.getTo(),
                    walkPath(relation.getFrom(), relation.getTo(), joinsMap));
                cfg.relations.put(relation.getTo() + "." + relation.getFrom(),
                    walkPath(relation.getTo(), relation.getFrom(), joinsMap));
            }
        }
    }

    private List<Join> walkPath(String from, String to, HashMap<String, List<Join>> joinsMap) {
        List<Join> path = new ArrayList<>();
        Set<String> done = new HashSet<>();
        String cur = from;
        while (!cur.equals(to)) {
            List<Join> candidates = joinsMap.get(cur);
            Join next = null;
            if (candidates.size() == 1) {
                next = candidates.get(0);
            } else {
                for (Join j : candidates) {
                    if (!done.contains(j.getSecond_table())) {
                        next = j;
                        break;
                    }
                }
            }
            if (next == null) break;
            path.add(next);
            done.add(cur);
            cur = next.getSecond_table();
        }
        return path;
    }

    private void buildEntities(Mapping mapping, ProfileConfiguration cfg) {
        Set<String> visible = new HashSet<>();
        for (MappingEntity entity : mapping.getEntities()) {
            if (entity.isVisible()) visible.add(entity.getName());
        }
        for (MappingEntity entity : mapping.getEntities()) {
            if (!entity.isVisible()) continue;
            Entity schemaEntity = new Entity(entity.getName());
            for (MappingField field : entity.getFields()) {
                if (field.isVisible()) {
                    schemaEntity.addField(new EntityField(field.getName(), field.getDatatype()));
                }
            }
            for (String rel : entity.getRelations()) {
                if (visible.contains(rel)) schemaEntity.addRelation(rel);
            }
            cfg.entities.put(entity.getName(), schemaEntity);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String map(Query query, List<Object> parameters, String orderBy) {
        String profile = query.getProfile() != null ? query.getProfile() : primaryProfile;
        return new SqlQueryBuilder(query, profileConfigurations.get(profile))
            .getSqlQuery(parameters, orderBy);
    }

    public HashMap<String, Field> getFields(String profile) {
        String key = profile != null ? profile : primaryProfile;
        return profileConfigurations.get(key).fields;
    }

    public HashMap<String, Entity> getEntities(String profile) {
        String key = profile != null ? profile : primaryProfile;
        return profileConfigurations.get(key).entities;
    }

    public List<Profile> getProfiles() {
        return profiles;
    }

    public ProfileConfiguration getProfileConfiguration(String profile) {
        String key = profile != null ? profile : primaryProfile;
        return profileConfigurations.get(key);
    }
}
