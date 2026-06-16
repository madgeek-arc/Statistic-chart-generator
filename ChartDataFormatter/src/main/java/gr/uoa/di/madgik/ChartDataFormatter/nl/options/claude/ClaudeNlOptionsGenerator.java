package gr.uoa.di.madgik.ChartDataFormatter.nl.options.claude;

import gr.uoa.di.madgik.ChartDataFormatter.nl.options.NlOptionsGenerator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ClaudeNlOptionsGenerator implements NlOptionsGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a chart configuration expert. Given a description of chart appearance,
            generate a valid %s options JSON object.

            Rules:
            - Output ONLY valid JSON. No markdown fences, no explanation.
            - Use only valid %s option names and structures.
            - Do not include data (series, dataset) — only visual and layout options.
            - Only include properties that are explicitly described. Do not invent defaults
              or placeholders for anything not mentioned in the description.
            - Never include title.text or subtitle.text — those are always set by the caller.
            """;

    private final ChatClient chatClient;

    public ClaudeNlOptionsGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(String library, String canonicalDescription) {
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(library, library))
                .user(canonicalDescription)
                .call()
                .content();
        return stripFences(response.strip());
    }

    private String stripFences(String s) {
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").strip();
        }
        return s;
    }
}
