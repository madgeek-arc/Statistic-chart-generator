package gr.uoa.di.madgik.ChartDataFormatter.nl.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlSqlGenerator;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchema;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchemaBuilder;
import gr.uoa.di.madgik.ChartDataFormatter.nl.SqlResult;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ClaudeNlSqlGenerator implements NlSqlGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a SQL generation assistant. Given a natural language query and a database schema,
            produce a single Impala-compatible SELECT prepared statement with ? placeholders.

            Rules:
            - Output ONLY valid JSON: {"sql": "...", "parameters": [...], "description": "..."}
            - Use only tables and columns present in the schema.
            - Parameters must be ordered to match ? placeholders.
            - Never use scalar subqueries in SELECT (Impala limitation).
            - Use COUNT(DISTINCT ...) rather than COUNT(*) for entity counts.
            - The "description" field must be one plain-English sentence describing what data the
              query returns, written for a non-technical user (e.g. "Number of open-access
              publications grouped by year of publication").
            - Do not include any explanation outside the JSON.
            """;

    private final ChatClient chatClient;
    private final ProfileSchemaBuilder schemaBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeNlSqlGenerator(ChatClient.Builder chatClientBuilder,
                                ProfileSchemaBuilder schemaBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.schemaBuilder = schemaBuilder;
    }

    @Override
    public SqlResult generate(String canonicalNl, String profile, ProfileSchema schema,
                              List<FilterGroup> extraFilters) {
        String schemaDescription = buildSchemaDescription(schema);
        String filterSection = buildFilterSection(profile, extraFilters);
        String userPrompt = "Schema:\n" + schemaDescription
                + "\n\nQuery: " + canonicalNl
                + (filterSection.isEmpty() ? "" : "\n\n" + filterSection);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return parseResponse(response);
    }

    SqlResult parseResponse(String response) {
        try {
            String json = response.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }
            JsonNode node = objectMapper.readTree(json);
            String sql = node.get("sql").asText();
            List<Object> params = new ArrayList<>();
            for (JsonNode p : node.get("parameters")) {
                params.add(p.isNumber() ? p.numberValue() : p.asText());
            }
            String description = node.has("description") ? node.get("description").asText() : "";
            return new SqlResult(sql, params, description);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM SQL response: " + response, e);
        }
    }

    private String buildFilterSection(String profile, List<FilterGroup> extraFilters) {
        if (extraFilters == null || extraFilters.isEmpty()) return "";
        List<ProfileSchemaBuilder.ResolvedFilter> resolved =
                schemaBuilder.resolveFilterFields(profile, extraFilters);
        if (resolved.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(
            "Required additional conditions (pre-resolved from the schema — incorporate these " +
            "exactly into the SQL, adding any necessary JOINs):\n");
        for (ProfileSchemaBuilder.ResolvedFilter rf : resolved) {
            // embed parameter values directly — LLM uses them as ? placeholders
            String valuesStr = rf.params().stream()
                    .map(v -> "'" + v + "'")
                    .collect(Collectors.joining(", "));
            sb.append("  - ").append(rf.sqlCondition().replace("?", valuesStr));
            if (!rf.joinHint().isEmpty()) {
                sb.append("\n    join: ").append(rf.joinHint());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildSchemaDescription(ProfileSchema schema) {
        StringBuilder sb = new StringBuilder();
        for (ProfileSchema.EntityDef entity : schema.getEntities()) {
            sb.append("Entity: ").append(entity.name())
              .append(" (SQL table: ").append(entity.sqlTable()).append(")")
              .append(" — ").append(entity.description()).append("\n");
            if (!entity.baseConditions().isEmpty()) {
                sb.append("  REQUIRED base conditions (always include in WHERE): ")
                  .append(String.join(" AND ", entity.baseConditions())).append("\n");
            }
            for (ProfileSchema.FieldDef field : entity.fields()) {
                sb.append("  field: ").append(field.name())
                  .append(" (").append(field.datatype()).append(")")
                  .append(" — SQL: ").append(field.sqlTable()).append(".").append(field.column()).append("\n");
            }
            if (!entity.joinPaths().isEmpty()) {
                sb.append("  joins:\n");
                entity.joinPaths().forEach(jp -> sb.append("    ").append(jp).append("\n"));
            }
        }
        return sb.toString();
    }
}
