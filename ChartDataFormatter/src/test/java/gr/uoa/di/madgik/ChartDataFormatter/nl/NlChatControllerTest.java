package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.RestControllers.NlChatController;
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

public class NlChatControllerTest {

    private MockMvc mockMvc;
    private NlQueryAgent nlQueryAgent;
    private ConversationSessionStore sessionStore;

    @BeforeEach
    void setup() {
        nlQueryAgent = mock(NlQueryAgent.class);
        sessionStore = mock(ConversationSessionStore.class);

        ConversationSession session = new ConversationSession("session-123", "openaire");
        when(sessionStore.get(any())).thenReturn(null); // no existing session
        when(sessionStore.create(any())).thenReturn(session);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new NlChatController(nlQueryAgent, sessionStore))
                .build();
    }

    @Test
    void chat_missingProfile_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"show me publications\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(nlQueryAgent);
    }

    @Test
    void chat_missingMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"openaire\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(nlQueryAgent);
    }

    @Test
    void chat_inProgress_returnsDoneFalseWithNoFinalFields() throws Exception {
        when(nlQueryAgent.chat(any(), any(), any()))
                .thenReturn(new AgentReply("What filters do you need?", false, null, null, null));

        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"openaire\",\"message\":\"show me publications\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.reply").value("What filters do you need?"))
                .andExpect(jsonPath("$.canonicalNl").doesNotExist())
                .andExpect(jsonPath("$.sig").doesNotExist())
                .andExpect(jsonPath("$.sql").doesNotExist());
    }

    @Test
    void chat_done_returnsCanonicalNlSigAndSql() throws Exception {
        when(nlQueryAgent.chat(any(), any(), any()))
                .thenReturn(new AgentReply(
                        "Here is your query.",
                        true,
                        "Publications per year",
                        "abc123sig",
                        "SELECT year, COUNT(*) FROM result GROUP BY year"
                ));

        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"openaire\",\"message\":\"yes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.canonicalNl").value("Publications per year"))
                .andExpect(jsonPath("$.sig").value("abc123sig"))
                .andExpect(jsonPath("$.sql").value("SELECT year, COUNT(*) FROM result GROUP BY year"));
    }

    @Test
    void chat_returnsSessionId() throws Exception {
        when(nlQueryAgent.chat(any(), any(), any()))
                .thenReturn(new AgentReply("Got it.", false, null, null, null));

        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"openaire\",\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"));
    }

    @Test
    void chat_withExistingSession_reusesThatSession() throws Exception {
        ConversationSession existing = new ConversationSession("existing-session", "openaire");
        when(sessionStore.get("existing-session")).thenReturn(existing);
        when(nlQueryAgent.chat(any(), any(), any()))
                .thenReturn(new AgentReply("Reply.", false, null, null, null));

        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"existing-session\",\"profile\":\"openaire\",\"message\":\"next message\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("existing-session"));

        verify(sessionStore, never()).create(any());
        verify(nlQueryAgent).chat(eq("existing-session"), eq("next message"), eq("openaire"));
    }

    @Test
    void chat_agentIsCalledWithCorrectArgs() throws Exception {
        when(nlQueryAgent.chat(any(), any(), any()))
                .thenReturn(new AgentReply("Ok.", false, null, null, null));

        mockMvc.perform(post("/nl/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"openaire\",\"message\":\"total datasets\"}"))
                .andExpect(status().isOk());

        verify(nlQueryAgent).chat(eq("session-123"), eq("total datasets"), eq("openaire"));
    }
}
