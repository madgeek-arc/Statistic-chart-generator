package gr.uoa.di.madgik.ChartDataFormatter.nl.claude;

import gr.uoa.di.madgik.ChartDataFormatter.nl.AgentReply;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlQueryAgent;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSession;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSessionStore;
import gr.uoa.di.madgik.ChartDataFormatter.nl.mcp.NlMcpTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClaudeNlQueryAgent implements NlQueryAgent {

    private static final String SYSTEM_PROMPT = """
            You are a data query assistant helping users describe their data query in natural language.
            You have access to tools to explore the profile schema and sample field values.

            Your goal:
            1. Understand what data the user wants to retrieve.
            2. Ask clarifying questions if needed (entity, filters, grouping, aggregation).
            3. When you have a clear, unambiguous description, produce a canonical English summary
               of the query (e.g. "Number of open access publications per year in Greece").
            4. Confirm with the user, then call sign_nl_query to finalise.

            When calling sign_nl_query, the canonicalNl argument must be a concise, precise English
            description of the query that fully captures the intent, filters, and aggregation.
            """;

    private final ChatClient chatClient;
    private final ConversationSessionStore sessionStore;

    public ClaudeNlQueryAgent(ChatClient.Builder chatClientBuilder,
                              ConversationSessionStore sessionStore,
                              NlMcpTools tools) {
        this.chatClient = chatClientBuilder
                .defaultTools(tools)
                .build();
        this.sessionStore = sessionStore;
    }

    @Override
    public AgentReply chat(String sessionId, String userMessage, String profile) {
        ConversationSession session = sessionStore.get(sessionId);
        if (session == null) {
            session = sessionStore.create(profile);
        }

        session.addMessage("user", userMessage);

        List<Message> messages = buildMessages(session);
        String response = chatClient.prompt()
                .messages(messages)
                .call()
                .content();

        session.addMessage("assistant", response);

        boolean done = response.contains("sign_nl_query") || sessionSignedUrl(response) != null;
        String canonicalNl = done ? extractCanonicalNl(response) : null;

        if (done) {
            sessionStore.remove(session.getSessionId());
        }

        return new AgentReply(response, done, canonicalNl);
    }

    private List<Message> buildMessages(ConversationSession session) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (ConversationSession.Message m : session.getHistory()) {
            if ("user".equals(m.role())) {
                messages.add(new UserMessage(m.content()));
            } else {
                messages.add(new AssistantMessage(m.content()));
            }
        }
        return messages;
    }

    private String sessionSignedUrl(String response) {
        // Signed URL contains &sig= — if the agent embedded it in the reply the conversation is done
        return response.contains("&sig=") ? response : null;
    }

    private String extractCanonicalNl(String response) {
        // The agent embeds the URL in its reply; canonical NL is the nl= param value
        int nlIdx = response.indexOf("nl=");
        if (nlIdx < 0) return null;
        int sigIdx = response.indexOf("&sig=", nlIdx);
        if (sigIdx < 0) return null;
        String encoded = response.substring(nlIdx + 3, sigIdx);
        return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }
}
