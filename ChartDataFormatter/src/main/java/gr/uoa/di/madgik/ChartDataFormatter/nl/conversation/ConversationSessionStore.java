package gr.uoa.di.madgik.ChartDataFormatter.nl.conversation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationSessionStore {

    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ConversationSessionStore(@Value("${nl.session-ttl-minutes:30}") int ttlMinutes) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public ConversationSession create(String profile) {
        String id = UUID.randomUUID().toString();
        ConversationSession session = new ConversationSession(id, profile);
        sessions.put(id, session);
        return session;
    }

    public ConversationSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    @Scheduled(fixedDelayString = "${nl.session-cleanup-interval-ms:60000}")
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(ttl);
        sessions.entrySet().removeIf(e -> e.getValue().getLastAccess().isBefore(cutoff));
    }
}
