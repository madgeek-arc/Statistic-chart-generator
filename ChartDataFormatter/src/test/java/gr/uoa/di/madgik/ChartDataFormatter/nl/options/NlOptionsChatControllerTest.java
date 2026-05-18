package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

import gr.uoa.di.madgik.ChartDataFormatter.RestControllers.NlOptionsChatController;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSession;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class NlOptionsChatControllerTest {

    private MockMvc mockMvc;
    private NlOptionsChatAgent agent;
    private ConversationSessionStore sessionStore;

    @BeforeEach
    void setup() {
        agent = mock(NlOptionsChatAgent.class);
        sessionStore = mock(ConversationSessionStore.class);

        ConversationSession session = new ConversationSession("session-123", "HighCharts");
        when(sessionStore.get(any())).thenReturn(null);
        when(sessionStore.create(any())).thenReturn(session);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new NlOptionsChatController(agent, sessionStore))
                .build();
    }

    @Test
    void chat_missingLibrary_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"blue bar chart\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agent);
    }

    @Test
    void chat_missingMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"HighCharts\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agent);
    }

    @Test
    void chat_inProgress_returnsDoneFalseWithNoFinalFields() throws Exception {
        when(agent.chat(any(), any(), any()))
                .thenReturn(new OptionsAgentReply("What title would you like?", false, null, null, null));

        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"HighCharts\",\"message\":\"make it blue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.reply").value("What title would you like?"))
                .andExpect(jsonPath("$.canonicalDescription").doesNotExist())
                .andExpect(jsonPath("$.sig").doesNotExist())
                .andExpect(jsonPath("$.optionsJson").doesNotExist());
    }

    @Test
    void chat_done_returnsCanonicalDescriptionSigAndOptionsJson() throws Exception {
        String optionsJson = "{\"colors\":[\"blue\"]}";
        when(agent.chat(any(), any(), any()))
                .thenReturn(new OptionsAgentReply(
                        "Here are your chart options.",
                        true,
                        "blue bar chart with red title",
                        "abc123sig",
                        optionsJson
                ));

        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"HighCharts\",\"message\":\"yes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.canonicalDescription").value("blue bar chart with red title"))
                .andExpect(jsonPath("$.sig").value("abc123sig"))
                .andExpect(jsonPath("$.optionsJson").value(optionsJson));
    }

    @Test
    void chat_returnsSessionId() throws Exception {
        when(agent.chat(any(), any(), any()))
                .thenReturn(new OptionsAgentReply("Got it.", false, null, null, null));

        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"HighCharts\",\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"));
    }

    @Test
    void chat_withExistingSession_reusesThatSession() throws Exception {
        ConversationSession existing = new ConversationSession("existing-session", "HighCharts");
        when(sessionStore.get("existing-session")).thenReturn(existing);
        when(agent.chat(any(), any(), any()))
                .thenReturn(new OptionsAgentReply("Reply.", false, null, null, null));

        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"existing-session\",\"library\":\"HighCharts\",\"message\":\"darker blue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("existing-session"));

        verify(sessionStore, never()).create(any());
        verify(agent).chat(eq("existing-session"), eq("darker blue"), eq("HighCharts"));
    }

    @Test
    void chat_agentIsCalledWithCorrectArgs() throws Exception {
        when(agent.chat(any(), any(), any()))
                .thenReturn(new OptionsAgentReply("Ok.", false, null, null, null));

        mockMvc.perform(post("/nl/options/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"eCharts\",\"message\":\"green pie chart\"}"))
                .andExpect(status().isOk());

        verify(agent).chat(eq("session-123"), eq("green pie chart"), eq("eCharts"));
    }
}
