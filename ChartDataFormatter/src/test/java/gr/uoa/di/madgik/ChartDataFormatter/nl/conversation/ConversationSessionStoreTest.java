package gr.uoa.di.madgik.ChartDataFormatter.nl.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ConversationSessionStoreTest {

    private ConversationSessionStore store;

    @BeforeEach
    void setup() {
        store = new ConversationSessionStore(30);
    }

    @Test
    void create_returnsSessionWithCorrectProfile() {
        ConversationSession session = store.create("openaire");
        assertNotNull(session);
        assertEquals("openaire", session.getProfile());
        assertNotNull(session.getSessionId());
    }

    @Test
    void get_returnsCreatedSession() {
        ConversationSession created = store.create("openaire");
        ConversationSession retrieved = store.get(created.getSessionId());
        assertSame(created, retrieved);
    }

    @Test
    void get_unknownId_returnsNull() {
        assertNull(store.get("non-existent-id"));
    }

    @Test
    void remove_deletesSession() {
        ConversationSession session = store.create("openaire");
        store.remove(session.getSessionId());
        assertNull(store.get(session.getSessionId()));
    }

    @Test
    void create_eachCall_producesUniqueId() {
        ConversationSession s1 = store.create("openaire");
        ConversationSession s2 = store.create("openaire");
        assertNotEquals(s1.getSessionId(), s2.getSessionId());
    }

    @Test
    void evictExpired_removesOldSessions() throws Exception {
        ConversationSessionStore shortTtlStore = new ConversationSessionStore(1);
        ConversationSession session = shortTtlStore.create("openaire");

        setLastAccess(session, Instant.now().minusSeconds(120));

        shortTtlStore.evictExpired();

        assertNull(shortTtlStore.get(session.getSessionId()));
    }

    @Test
    void evictExpired_keepsRecentSessions() throws Exception {
        ConversationSession session = store.create("openaire");
        store.evictExpired();
        assertNotNull(store.get(session.getSessionId()));
    }

    @Test
    void addMessage_updatesLastAccess() throws Exception {
        ConversationSession session = store.create("openaire");
        Instant before = session.getLastAccess();

        Thread.sleep(10);
        session.addMessage("user", "hello");

        assertTrue(session.getLastAccess().isAfter(before));
    }

    private void setLastAccess(ConversationSession session, Instant time) throws Exception {
        Field f = ConversationSession.class.getDeclaredField("lastAccess");
        f.setAccessible(true);
        f.set(session, time);
    }
}
