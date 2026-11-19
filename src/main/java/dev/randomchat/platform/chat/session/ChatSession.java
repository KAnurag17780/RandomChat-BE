package dev.randomchat.platform.chat.session;

import dev.randomchat.platform.chat.handler.SessionRateLimiter;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime state for one authenticated WebSocket connection.
 * <p>
 * This object lives in memory only. It is created when a client successfully
 * completes the WebSocket handshake and destroyed when cleanup is finished.
 * <p>
 * Thread-safety: {@code state} is an {@link AtomicReference} for CAS-based
 * state transitions. All multi-step transitions are coordinated under the
 * matchmaking lock held by {@link dev.randomchat.platform.chat.matchmaking.MatchmakingEngine}.
 * The decorated session is safe for concurrent writes.
 */
public class ChatSession {

    private final String sessionId;
    private final UUID userId;
    private final ConcurrentWebSocketSessionDecorator decoratedSession;
    private final Instant connectedAt;

    /** Current lifecycle state – use CAS via the matchmaking lock, not raw set. */
    private final AtomicReference<UserState> state = new AtomicReference<>(UserState.CONNECTED);

    /** ID of the active pair this session belongs to, if any. */
    private volatile UUID activePairId;

    /** Timestamp of the last heartbeat ping received from the client. */
    private volatile Instant lastHeartbeatAt;

    /**
     * Per-session rate limiter for SEND actions.
     * Allows up to 30 messages per 10-second sliding window.
     */
    private final SessionRateLimiter sendRateLimiter = new SessionRateLimiter(30, 10_000);

    /**
     * @param sendTimeLimit   maximum milliseconds the underlying send may block
     * @param sendBufferSize  maximum bytes to buffer for a slow client
     */
    public ChatSession(WebSocketSession rawSession,
                       UUID userId,
                       int sendTimeLimit,
                       int sendBufferSize) {
        this.sessionId = rawSession.getId();
        this.userId = userId;
        this.decoratedSession = new ConcurrentWebSocketSessionDecorator(
                rawSession, sendTimeLimit, sendBufferSize);
        this.connectedAt = Instant.now();
        this.lastHeartbeatAt = Instant.now();
    }

    // -----------------------------------------------------------------------
    // State management
    // -----------------------------------------------------------------------

    public UserState getState() {
        return state.get();
    }

    /**
     * CAS-style transition. Returns {@code true} if the transition succeeded.
     */
    public boolean compareAndSetState(UserState expected, UserState next) {
        return state.compareAndSet(expected, next);
    }

    /**
     * Force-sets state. Only for terminal transitions (DISCONNECTING → DISCONNECTED)
     * where the matchmaking lock is already held.
     */
    public void setState(UserState next) {
        state.set(next);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ConcurrentWebSocketSessionDecorator getDecoratedSession() {
        return decoratedSession;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public UUID getActivePairId() {
        return activePairId;
    }

    public void setActivePairId(UUID activePairId) {
        this.activePairId = activePairId;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void updateHeartbeat() {
        this.lastHeartbeatAt = Instant.now();
    }

    public boolean isOpen() {
        return decoratedSession.isOpen();
    }

    /**
     * Returns the per-session rate limiter for SEND actions.
     * Callers should invoke {@link SessionRateLimiter#tryAcquire()} before processing a SEND.
     */
    public SessionRateLimiter getSendRateLimiter() {
        return sendRateLimiter;
    }

    @Override
    public String toString() {
        return "ChatSession{sessionId='" + sessionId + "', userId=" + userId +
                ", state=" + state.get() + '}';
    }
}
