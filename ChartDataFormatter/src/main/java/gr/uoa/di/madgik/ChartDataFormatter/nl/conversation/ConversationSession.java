package gr.uoa.di.madgik.ChartDataFormatter.nl.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ConversationSession {

    public record Message(String role, String content) {}

    private final String sessionId;
    private final String profile;
    private final List<Message> history = new ArrayList<>();
    private volatile Instant lastAccess;

    public ConversationSession(String sessionId, String profile) {
        this.sessionId = sessionId;
        this.profile = profile;
        this.lastAccess = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public String getProfile() { return profile; }
    public List<Message> getHistory() { return history; }

    public void addMessage(String role, String content) {
        history.add(new Message(role, content));
        lastAccess = Instant.now();
    }

    public Instant getLastAccess() { return lastAccess; }
}
