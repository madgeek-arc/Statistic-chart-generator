package gr.uoa.di.madgik.ChartDataFormatter.nl.claude;

import gr.uoa.di.madgik.ChartDataFormatter.nl.ProfileSchemaBuilder;
import gr.uoa.di.madgik.ChartDataFormatter.nl.SqlResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClaudeNlSqlGeneratorParseTest {

    private ClaudeNlSqlGenerator generator;

    @BeforeEach
    void setup() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        generator = new ClaudeNlSqlGenerator(builder, mock(ProfileSchemaBuilder.class));
    }

    @Test
    void parsePlainJson_returnsCorrectSqlAndParameters() {
        String response = """
                {"sql": "SELECT COUNT(*) FROM result WHERE year=?", "parameters": ["2023"]}
                """;
        SqlResult result = generator.parseResponse(response);
        assertEquals("SELECT COUNT(*) FROM result WHERE year=?", result.getSql());
        assertEquals(List.of("2023"), result.getParameters());
    }

    @Test
    void parseMarkdownWrappedJson_stripsBackticks() {
        String response = """
                ```json
                {"sql": "SELECT COUNT(*) FROM result", "parameters": []}
                ```
                """;
        SqlResult result = generator.parseResponse(response);
        assertEquals("SELECT COUNT(*) FROM result", result.getSql());
        assertTrue(result.getParameters().isEmpty());
    }

    @Test
    void parseMultipleParameters_preservesOrder() {
        String response = """
                {"sql": "SELECT COUNT(*) FROM result WHERE type=? AND year>?", "parameters": ["publication", "2020"]}
                """;
        SqlResult result = generator.parseResponse(response);
        assertEquals(List.of("publication", "2020"), result.getParameters());
    }

    @Test
    void parseNumericParameter_preservedAsNumber() {
        String response = """
                {"sql": "SELECT COUNT(*) FROM result WHERE year=?", "parameters": [2023]}
                """;
        SqlResult result = generator.parseResponse(response);
        assertEquals(1, result.getParameters().size());
        assertTrue(result.getParameters().get(0) instanceof Number);
        assertEquals(2023, ((Number) result.getParameters().get(0)).intValue());
    }

    @Test
    void parseEmptyParameters_returnsEmptyList() {
        String response = """
                {"sql": "SELECT COUNT(*) FROM result", "parameters": []}
                """;
        SqlResult result = generator.parseResponse(response);
        assertTrue(result.getParameters().isEmpty());
    }

    @Test
    void parseInvalidJson_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                generator.parseResponse("this is not json"));
    }

    @Test
    void parseMissingSqlField_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                generator.parseResponse("{\"parameters\": []}"));
    }

    @Test
    void parseMarkdownWithoutLanguageTag_stripsBackticks() {
        String response = "```\n{\"sql\": \"SELECT 1 FROM result\", \"parameters\": []}\n```";
        SqlResult result = generator.parseResponse(response);
        assertEquals("SELECT 1 FROM result", result.getSql());
    }
}
