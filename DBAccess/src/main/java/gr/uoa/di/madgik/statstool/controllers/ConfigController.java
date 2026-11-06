package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.config.DataSourceProperty;
import gr.uoa.di.madgik.statstool.config.DynamicDataSourceRegistry;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.domain.Mapping;
import gr.uoa.di.madgik.statstool.mapping.domain.MappingProfile;
import gr.uoa.di.madgik.statstool.repositories.ConfigRepository;
import gr.uoa.di.madgik.statstool.repositories.NamedQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API for managing dynamic configuration stored in the config-db.
 * All mutating endpoints take effect immediately without a restart:
 *   - Datasource changes:    new HikariCP pool is opened / old one closed
 *   - Profile / query changes: in-memory caches are reloaded
 *
 * Security: protected by OIDC JWT (see SecurityConfig).
 *   GET  /config/**  → authenticated
 *   POST/PUT/DELETE  → ADMIN role required
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    @Autowired private ConfigRepository configRepository;
    @Autowired private DynamicDataSourceRegistry registry;
    @Autowired private Mapper mapper;
    @Autowired private NamedQueryRepository namedQueryRepository;

    // =========================================================================
    // Datasources
    // =========================================================================

    @GetMapping("/datasources")
    public List<DatasourceView> listDatasources() {
        return configRepository.loadDatasources().stream()
            .map(DatasourceView::from)
            .toList();
    }

    @GetMapping("/datasources/active")
    public Set<String> activeIds() {
        return registry.getIds();
    }

    @PostMapping("/datasources")
    public ResponseEntity<Void> createDatasource(@RequestBody DataSourceProperty props) {
        configRepository.saveDatasource(props);
        registry.register(props);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/datasources/{id}")
    public ResponseEntity<Void> updateDatasource(
            @PathVariable String id, @RequestBody DataSourceProperty props) {
        props.setId(id);
        configRepository.saveDatasource(props);
        registry.register(props); // closes old pool, opens new
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/datasources/{id}")
    public ResponseEntity<Void> deleteDatasource(@PathVariable String id) {
        configRepository.deleteDatasource(id);
        registry.deregister(id);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // Mapping profiles
    // =========================================================================

    @GetMapping("/profiles")
    public List<ProfileMetaView> listProfiles() {
        return configRepository.loadProfiles().stream()
            .map(ProfileMetaView::from)
            .toList();
    }

    @GetMapping("/profiles/{name}")
    public ResponseEntity<MappingProfile> getProfile(@PathVariable String name) {
        return configRepository.loadProfiles().stream()
            .filter(p -> p.getName().equals(name))
            .findFirst()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/profiles")
    public ResponseEntity<Void> createProfile(@RequestBody ProfileRequest req) {
        configRepository.saveProfile(req.toMappingProfile());
        mapper.reload();
        return ResponseEntity.ok().build();
    }

    @PutMapping("/profiles/{name}")
    public ResponseEntity<Void> updateProfile(
            @PathVariable String name, @RequestBody ProfileRequest req) {
        req.name = name;
        configRepository.saveProfile(req.toMappingProfile());
        mapper.reload();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/profiles/{name}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String name) {
        configRepository.deleteProfile(name);
        mapper.reload();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/profiles/reload")
    public ResponseEntity<Void> reloadProfiles() {
        mapper.reload();
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // Named queries
    // =========================================================================

    @GetMapping("/queries")
    public Map<String, String> listQueries() {
        return configRepository.loadNamedQueries();
    }

    @GetMapping("/queries/{name}")
    public ResponseEntity<String> getQuery(@PathVariable String name) {
        Map<String, String> all = configRepository.loadNamedQueries();
        String q = all.get(name);
        return q != null ? ResponseEntity.ok(q) : ResponseEntity.notFound().build();
    }

    @PostMapping("/queries")
    public ResponseEntity<Void> createQuery(@RequestBody NamedQueryRequest req) {
        configRepository.saveNamedQuery(req.name(), req.query(), req.description());
        namedQueryRepository.reload();
        return ResponseEntity.ok().build();
    }

    @PutMapping("/queries/{name}")
    public ResponseEntity<Void> updateQuery(
            @PathVariable String name, @RequestBody NamedQueryRequest req) {
        configRepository.saveNamedQuery(name, req.query(), req.description());
        namedQueryRepository.reload();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/queries/{name}")
    public ResponseEntity<Void> deleteQuery(@PathVariable String name) {
        configRepository.deleteNamedQuery(name);
        namedQueryRepository.reload();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/queries/reload")
    public ResponseEntity<Void> reloadQueries() {
        namedQueryRepository.reload();
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    /** Datasource view that never exposes the password. */
    public record DatasourceView(
        String id, String url, String driverClass,
        String username, int maxPoolSize, int connTimeoutMs) {

        static DatasourceView from(DataSourceProperty p) {
            return new DatasourceView(
                p.getId(), p.getUrl(), p.getDriverClassName(),
                p.getUsername(), p.getMaxPoolSize(), p.getConnTimeoutMs());
        }
    }

    /** Profile list entry — metadata only, no large mapping_content. */
    public record ProfileMetaView(
        String name, String description, String usage,
        List<String> shareholders, int complexity,
        boolean primary, boolean hidden) {

        static ProfileMetaView from(MappingProfile p) {
            return new ProfileMetaView(
                p.getName(), p.getDescription(), p.getUsage(),
                p.getShareholders(), p.getComplexity(),
                p.isPrimary(), p.isHidden());
        }
    }

    /** Request body for creating / updating a mapping profile. */
    public static class ProfileRequest {
        public String name;
        public String description;
        public String usage;
        public List<String> shareholders;
        public int complexity;
        public boolean primary;
        public boolean hidden;
        public Mapping mappingContent;

        MappingProfile toMappingProfile() {
            MappingProfile p = new MappingProfile();
            p.setName(name);
            p.setDescription(description);
            p.setUsage(usage);
            p.setShareholders(shareholders);
            p.setComplexity(complexity);
            p.setPrimary(primary);
            p.setHidden(hidden);
            p.setContent(mappingContent);
            return p;
        }
    }

    /** Request body for creating / updating a named query. */
    public record NamedQueryRequest(String name, String query, String description) {}
}
