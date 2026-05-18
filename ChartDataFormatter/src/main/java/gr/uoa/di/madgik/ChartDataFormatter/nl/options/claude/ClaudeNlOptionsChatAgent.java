package gr.uoa.di.madgik.ChartDataFormatter.nl.options.claude;

import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSession;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSessionStore;
import gr.uoa.di.madgik.ChartDataFormatter.nl.options.NlOptionsChatAgent;
import gr.uoa.di.madgik.ChartDataFormatter.nl.options.OptionsAgentReply;
import gr.uoa.di.madgik.ChartDataFormatter.nl.options.mcp.NlOptionsMcpTools;
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
public class ClaudeNlOptionsChatAgent implements NlOptionsChatAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a chart appearance configurator for %s charts.
            Help the user design their chart visually: titles, colors, axes, legend, fonts, themes, \
            and any other visual properties supported by %s.

            Your goal:
            1. Understand what the user wants the chart to look like.
            2. Generate a valid %s options JSON object reflecting their preferences.
            3. Use preview_options to show the user what the config looks like before finalising.
            4. Iterate until the user is satisfied with the appearance.
            5. When the user confirms, call sign_chart_options with:
               - library: "%s"
               - canonicalDescription: a complete, self-contained English description of ALL visual
                 settings chosen (must be readable standalone so options can be regenerated from it
                 alone without the conversation history)
               - optionsJson: the final %s options JSON object

            Important: do not include data (series, dataset) in the options — only visual/layout config.
            """;

    private final ChatClient chatClient;
    private final ConversationSessionStore sessionStore;

    public ClaudeNlOptionsChatAgent(ChatClient.Builder chatClientBuilder,
                                    ConversationSessionStore sessionStore,
                                    NlOptionsMcpTools tools,
                                    @Value("${nl.agent-model:claude-haiku-4-5-20251001}") String agentModel) {
        this.chatClient = chatClientBuilder
                .defaultOptions(AnthropicChatOptions.builder().model(agentModel).build())
                .defaultTools(tools)
                .build();
        this.sessionStore = sessionStore;
    }

    @Override
    public OptionsAgentReply chat(String sessionId, String userMessage, String library) {
        ConversationSession session = sessionStore.get(sessionId);
        if (session == null) {
            session = sessionStore.create(library);
        }

        session.addMessage("user", userMessage);

        NlOptionsMcpTools.clearSignedOptions();
        List<Message> messages = buildMessages(session);
        String response = chatClient.prompt()
                .messages(messages)
                .call()
                .content();
        NlOptionsMcpTools.SignedOptions signed = NlOptionsMcpTools.consumeSignedOptions();

        session.addMessage("assistant", response);

        boolean done = signed != null;
        if (done) {
            sessionStore.remove(session.getSessionId());
        }

        return new OptionsAgentReply(response, done,
                done ? signed.canonicalDescription() : null,
                done ? signed.sig() : null,
                done ? signed.optionsJson() : null);
    }

    private List<Message> buildMessages(ConversationSession session) {
        String library = session.getProfile();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT_TEMPLATE.formatted(
                library, library, library, library, library)));
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
