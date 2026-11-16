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
            - Use COUNT(DISTINCT ...) rather than COUNT(*) for entity counts.
            - The query must run on Impala (and DuckDB), so write only portable SQL and respect
              these Impala restrictions:
              * Never use scalar subqueries in SELECT.
              * Every non-aggregated SELECT expression must appear in GROUP BY. Any query with an
                aggregate plus a dimension column needs a GROUP BY on that column.
              * All COUNT(DISTINCT ...) in one SELECT must have the exact same argument. Never write
                COUNT(DISTINCT CASE WHEN ... THEN id END) more than once with different conditions.
                For conditional distinct counts, de-duplicate in a derived table first, then use
                plain COUNT with CASE:
                  SELECT COUNT(CASE WHEN t.year = ? THEN t.id END),
                         COUNT(CASE WHEN t.year = ? THEN t.id END), t.name
                  FROM (SELECT DISTINCT r.id, r.year, c.name FROM ... WHERE ...) t
                  GROUP BY t.name
              * Subqueries (IN / EXISTS) are allowed only in WHERE, combined with AND; never inside
                an OR. To match any of several conditions, use a single IN over a UNION ALL.
              * No FILTER (WHERE ...) on aggregates, no QUALIFY, no "::" casts (use
                CAST(x AS type)), no ILIKE (use lower(x) LIKE lower(?)), no || (use concat()).
            - COLUMN ORDER IS CRITICAL: always put the aggregate/numeric value FIRST, then the
              grouping dimension (category/label) SECOND. For example:
                SELECT COUNT(DISTINCT r.id), r.year  -- correct: value first, category second
                SELECT r.year, COUNT(DISTINCT r.id)  -- WRONG
              For two-dimension queries: SELECT value, dimension1, dimension2
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
        return parseResponse(call(buildUserPrompt(canonicalNl, profile, schema, extraFilters)));
    }

    @Override
    public SqlResult repair(String canonicalNl, String profile, ProfileSchema schema,
                            List<FilterGroup> extraFilters, SqlResult failed, String error) {
        return parseResponse(call(buildRepairPrompt(
                buildUserPrompt(canonicalNl, profile, schema, extraFilters), failed, error)));
    }

    static String buildRepairPrompt(String userPrompt, SqlResult failed, String error) {
        return userPrompt
                + "\n\nYour previous answer was rejected.\nSQL: " + failed.getSql()
                + "\nParameters: " + failed.getParameters()
                + "\nError: " + error
                + "\n\nReturn a corrected statement for the same query, following all the rules"
                + " (same JSON format).";
    }

    private String buildUserPrompt(String canonicalNl, String profile, ProfileSchema schema,
                                   List<FilterGroup> extraFilters) {
        String filterSection = buildFilterSection(profile, extraFilters);
        return "Schema:\n" + buildSchemaDescription(schema)
                + "\n\nQuery: " + canonicalNl
                + (filterSection.isEmpty() ? "" : "\n\n" + filterSection);
    }

    private String call(String userPrompt) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
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
