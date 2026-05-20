package gr.uoa.di.madgik.ChartDataFormatter.nl.mcp;

import gr.uoa.di.madgik.ChartDataFormatter.nl.NlSqlGenerator;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchema;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchemaBuilder;
import gr.uoa.di.madgik.ChartDataFormatter.nl.SqlResult;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.SqlSafetyValidator;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.repositories.NlCachedEntry;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.services.StatsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NlMcpTools {

    public record SignedQuery(String url, String canonicalNl, String sig, String sql, String description) {}

    private static final ThreadLocal<SignedQuery> pendingSign = new ThreadLocal<>();

    /** Called by ClaudeNlQueryAgent before each chatClient.call() to reset state. */
    public static void clearSignedQuery() {
        pendingSign.remove();
    }

    /** Called by ClaudeNlQueryAgent after chatClient.call() returns; clears the thread-local. */
    public static SignedQuery consumeSignedQuery() {
        SignedQuery result = pendingSign.get();
        pendingSign.remove();
        return result;
    }

    private final Mapper mapper;
    private final StatsService statsService;
    private final NlRequestSigner signer;
    private final NlSqlGenerator sqlGenerator;
    private final NlSqlCache nlSqlCache;
    private final ProfileSchemaBuilder schemaBuilder;
    private final String baseUrl;

    public NlMcpTools(Mapper mapper,
                      StatsService statsService,
                      NlRequestSigner signer,
                      @Lazy NlSqlGenerator sqlGenerator,
                      NlSqlCache nlSqlCache,
                      ProfileSchemaBuilder schemaBuilder,
                      @Value("${nl.base-url}") String baseUrl) {
        this.mapper = mapper;
        this.statsService = statsService;
        this.signer = signer;
        this.sqlGenerator = sqlGenerator;
        this.nlSqlCache = nlSqlCache;
        this.schemaBuilder = schemaBuilder;
        this.baseUrl = baseUrl;
    }

    @Tool(description = "Returns the list of available profiles with their name and description.")
    public List<Map<String, String>> getProfiles() {
        return mapper.getProfiles().stream()
                .map(p -> Map.of("name", p.getName(), "description", p.getDescription() != null ? p.getDescription() : ""))
                .collect(Collectors.toList());
    }

    @Tool(description = "Returns the schema of a profile: entities, their SQL table names, fields with SQL table/column, and relations between entities.")
    public ProfileSchema getSchema(String profile) {
        return schemaBuilder.build(profile);
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

    @Tool(description = "Generates SQL, signs the canonical NL query, caches the result, and returns a confirmation. Call this when the conversation is complete and the user is satisfied.")
    public String signNlQuery(String profile, String canonicalNl) {
        ProfileSchema schema = getSchema(profile);
        SqlResult sqlResult;
        try {
            sqlResult = sqlGenerator.generate(canonicalNl, profile, schema);
            SqlSafetyValidator.validate(sqlResult.getSql(), mapper.getProfileConfiguration(profile));
        } catch (Exception e) {
            return "ERROR: Could not generate valid SQL for this query: " + e.getMessage()
                    + ". Please refine the query description and try again.";
        }

        String fingerprint = NlSqlCache.fingerprint(mapper.getProfileConfiguration(profile));
        QueryWithParameters qwp = new QueryWithParameters(
                sqlResult.getSql(),
                new java.util.ArrayList<>(sqlResult.getParameters()),
                profile + ".public"
        );
        nlSqlCache.put(profile, canonicalNl, new NlCachedEntry(qwp, sqlResult.getDescription()), fingerprint);

        String sig = signer.sign(profile, canonicalNl);
        String url = baseUrl + "?profile=" + profile
                + "&nl=" + java.net.URLEncoder.encode(canonicalNl, java.nio.charset.StandardCharsets.UTF_8)
                + "&sig=" + sig;
        pendingSign.set(new SignedQuery(url, canonicalNl, sig, sqlResult.getSql(), sqlResult.getDescription()));
        return url;
    }
}
