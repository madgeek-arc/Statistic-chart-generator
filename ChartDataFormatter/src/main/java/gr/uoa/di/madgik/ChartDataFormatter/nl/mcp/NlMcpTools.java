package gr.uoa.di.madgik.ChartDataFormatter.nl.mcp;

import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchema;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.SqlSafetyValidator;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NlMcpTools {

    private final Mapper mapper;
    private final StatsService statsService;
    private final NlRequestSigner signer;
    private final String baseUrl;

    public NlMcpTools(Mapper mapper,
                      StatsService statsService,
                      NlRequestSigner signer,
                      @Value("${nl.base-url}") String baseUrl) {
        this.mapper = mapper;
        this.statsService = statsService;
        this.signer = signer;
        this.baseUrl = baseUrl;
    }

    @Tool(description = "Returns the schema of a profile: entities, their fields with datatypes and descriptions, and relations between entities.")
    public ProfileSchema getSchema(String profile) {
        Map<String, gr.uoa.di.madgik.statstool.mapping.entities.Entity> entities = mapper.getEntities(profile);
        List<ProfileSchema.EntityDef> defs = new ArrayList<>();
        for (var entry : entities.entrySet()) {
            var entity = entry.getValue();
            List<ProfileSchema.FieldDef> fields = entity.getFields().stream()
                    .map(f -> new ProfileSchema.FieldDef(f.getName(), f.getType(), f.getName()))
                    .collect(Collectors.toList());
            defs.add(new ProfileSchema.EntityDef(entity.getName(), entity.getName(), fields, entity.getRelations()));
        }
        return new ProfileSchema(profile, defs);
    }

    @Tool(description = "Returns up to 'limit' distinct sample values for the given field (format: 'entity.fieldName') in the specified profile.")
    public List<String> getFieldValues(String profile, String field, int limit) {
        try {
            Select select = new Select(field, "COUNT", 1);
            Query q = new Query(null, null, null, List.of(select), null, profile, limit, null, false);
            List<Result> results = statsService.query(List.of(q));
            if (results.isEmpty() || results.get(0).getRows().isEmpty()) return List.of();
            return results.get(0).getRows().stream()
                    .map(row -> String.valueOf(row.get(0)))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Tool(description = "Validates that the SQL is a safe SELECT referencing only known profile tables. Returns 'OK' or an error message.")
    public String validateSql(String profile, String sql) {
        try {
            ProfileConfiguration pc = mapper.getProfileConfiguration(profile);
            SqlSafetyValidator.validate(sql, pc);
            return "OK";
        } catch (IllegalArgumentException e) {
            return "INVALID: " + e.getMessage();
        }
    }

    @Tool(description = "Signs the canonical NL query and returns the chart URL. Call this when the conversation is complete and the user is satisfied.")
    public String signNlQuery(String profile, String canonicalNl) {
        String sig = signer.sign(profile, canonicalNl);
        return baseUrl + "?profile=" + profile
                + "&nl=" + java.net.URLEncoder.encode(canonicalNl, java.nio.charset.StandardCharsets.UTF_8)
                + "&sig=" + sig;
    }
}
