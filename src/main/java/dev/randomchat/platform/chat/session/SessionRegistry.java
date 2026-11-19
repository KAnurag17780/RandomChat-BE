package dev.randomchat.platform.chat.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of all active {@link ChatSession} objects.
 * <p>
 * Provides O(1) lookups by WebSocket session ID and by authenticated user ID.
 * The maps are eventually-consistent views; multi-step operations must be
 * coordinated under the matchmaking lock.
 * <p>
 * A user may have at most one active session per the matchmaking rules. The
 * second map ({@code sessionsByUserId}) stores only the <em>latest</em> session
 * for a given user; cleanup removes stale entries.
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    /** sessionId → ChatSession */
    private final ConcurrentHashMap<String, ChatSession> sessionsById = new ConcurrentHashMap<>();

    /** userId → sessionId (most recent active session) */
    private final ConcurrentHashMap<UUID, String> sessionIdByUserId = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public void register(ChatSession session) {
        sessionsById.put(session.getSessionId(), session);
        sessionIdByUserId.put(session.getUserId(), session.getSessionId());
        log.debug("Registered session: {}", session);
    }

    public void remove(String sessionId) {
        ChatSession removed = sessionsById.remove(sessionId);
        if (removed != null) {
            // Only remove userId mapping if it still points to this session
            sessionIdByUserId.remove(removed.getUserId(), sessionId);
            log.debug("Removed session: {}", removed);
        }
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public Optional<ChatSession> findById(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public Optional<ChatSession> findByUserId(UUID userId) {
        String sessionId = sessionIdByUserId.get(userId);
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public boolean hasActiveSession(UUID userId) {
        return sessionIdByUserId.containsKey(userId)
                && sessionsById.containsKey(sessionIdByUserId.get(userId));
    }

    public Collection<ChatSession> allSessions() {
        return sessionsById.values();
    }

    public int size() {
        return sessionsById.size();
    }
}
