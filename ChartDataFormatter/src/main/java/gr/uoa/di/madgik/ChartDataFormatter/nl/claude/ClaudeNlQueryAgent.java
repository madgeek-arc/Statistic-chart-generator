package gr.uoa.di.madgik.ChartDataFormatter.nl.claude;

import gr.uoa.di.madgik.ChartDataFormatter.nl.AgentReply;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlQueryAgent;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSession;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSessionStore;
import gr.uoa.di.madgik.ChartDataFormatter.nl.mcp.NlMcpTools;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClaudeNlQueryAgent implements NlQueryAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a data query assistant helping users describe their data query in natural language.
            You have access to tools to explore the profile schema and sample field values.
            The active data profile is: %s

            Start by calling get_schema with this profile to understand the available entities and fields.

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
                              NlMcpTools tools,
                              @Value("${nl.agent-model:claude-haiku-4-5-20251001}") String agentModel) {
        this.chatClient = chatClientBuilder
                .defaultOptions(AnthropicChatOptions.builder().model(agentModel).build())
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

        NlMcpTools.clearSignedQuery();
        List<Message> messages = buildMessages(session);
        String response = chatClient.prompt()
                .messages(messages)
                .call()
                .content();
        NlMcpTools.SignedQuery signed = NlMcpTools.consumeSignedQuery();

        session.addMessage("assistant", response);

        boolean done = signed != null;
        if (done) {
            sessionStore.remove(session.getSessionId());
        }

        return new AgentReply(response, done,
                done ? signed.canonicalNl() : null,
                done ? signed.sig() : null,
                done ? signed.sql() : null);
    }

    private List<Message> buildMessages(ConversationSession session) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT_TEMPLATE.formatted(session.getProfile())));
        for (ConversationSession.Message m : session.getHistory()) {
            if ("user".equals(m.role())) {
                messages.add(new UserMessage(m.content()));
            } else {
                messages.add(new AssistantMessage(m.content()));
            }
        }
        return messages;
    }
}
