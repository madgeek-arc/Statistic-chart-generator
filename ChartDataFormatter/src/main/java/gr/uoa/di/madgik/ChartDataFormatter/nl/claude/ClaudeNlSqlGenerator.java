package gr.uoa.di.madgik.ChartDataFormatter.nl.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlSqlGenerator;
import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchema;
import gr.uoa.di.madgik.ChartDataFormatter.nl.SqlResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClaudeNlSqlGenerator implements NlSqlGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a SQL generation assistant. Given a natural language query and a database schema,
            produce a single Impala-compatible SELECT prepared statement with ? placeholders.

            Rules:
            - Output ONLY valid JSON: {"sql": "...", "parameters": [...]}
            - Use only tables and columns present in the schema.
            - Parameters must be ordered to match ? placeholders.
            - Never use scalar subqueries in SELECT (Impala limitation).
            - Use COUNT(DISTINCT ...) rather than COUNT(*) for entity counts.
            - Do not include any explanation outside the JSON.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeNlSqlGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public SqlResult generate(String canonicalNl, String profile, ProfileSchema schema) {
        String schemaDescription = buildSchemaDescription(schema);
        String userPrompt = "Schema:\n" + schemaDescription + "\n\nQuery: " + canonicalNl;

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
            return new SqlResult(sql, params);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM SQL response: " + response, e);
        }
    }

    private String buildSchemaDescription(ProfileSchema schema) {
        StringBuilder sb = new StringBuilder();
        for (ProfileSchema.EntityDef entity : schema.getEntities()) {
            sb.append("Entity: ").append(entity.name())
              .append(" — ").append(entity.description()).append("\n");
            for (ProfileSchema.FieldDef field : entity.fields()) {
                sb.append("  field: ").append(field.name())
                  .append(" (").append(field.datatype()).append(")")
                  .append(" — ").append(field.description()).append("\n");
            }
            if (!entity.relations().isEmpty()) {
                sb.append("  relations: ").append(String.join(", ", entity.relations())).append("\n");
            }
        }
        return sb.toString();
    }
}
