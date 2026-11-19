package dev.randomchat.platform.chat.matchmaking;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable runtime record of an active 1-to-1 chat pair.
 * <p>
 * A {@code ChatPair} connects two {@link dev.randomchat.platform.chat.session.ChatSession}
 * objects for the duration of one chat conversation. It is purely in-memory runtime state;
 * the persistent conversation record lives in the {@code conversations} table.
 * <p>
 * Once {@code ended} is set to {@code true}, no new messages may be relayed
 * through this pair. Callers must check {@link #isEnded()} before relaying.
 */
public class ChatPair {

    private final UUID pairId;

    private final String sessionIdA;
    private final UUID userIdA;

    private final String sessionIdB;
    private final UUID userIdB;

    /** The persistent conversation ID for message history. */
    private final UUID conversationId;

    private final Instant createdAt;

    private volatile boolean ended = false;
    private volatile String endReason;

    public ChatPair(UUID pairId,
                    String sessionIdA, UUID userIdA,
                    String sessionIdB, UUID userIdB,
                    UUID conversationId) {
        this.pairId = pairId;
        this.sessionIdA = sessionIdA;
        this.userIdA = userIdA;
        this.sessionIdB = sessionIdB;
        this.userIdB = userIdB;
        this.conversationId = conversationId;
        this.createdAt = Instant.now();
    }

    // -----------------------------------------------------------------------
    // Partner resolution
    // -----------------------------------------------------------------------

    /**
     * Returns the partner's session ID for the given session ID.
     * Returns {@code null} if {@code sessionId} is not part of this pair.
     */
    public String getPartnerSessionId(String sessionId) {
        if (sessionIdA.equals(sessionId)) return sessionIdB;
        if (sessionIdB.equals(sessionId)) return sessionIdA;
        return null;
    }

    /**
     * Returns the partner's user ID for the given user ID.
     */
    public UUID getPartnerUserId(UUID userId) {
        if (userIdA.equals(userId)) return userIdB;
        if (userIdB.equals(userId)) return userIdA;
        return null;
    }

    public boolean containsSession(String sessionId) {
        return sessionIdA.equals(sessionId) || sessionIdB.equals(sessionId);
    }

    // -----------------------------------------------------------------------
    // End lifecycle
    // -----------------------------------------------------------------------

    /** Marks this pair as ended. Idempotent. */
    public synchronized void end(String reason) {
        if (!ended) {
            ended = true;
            endReason = reason;
        }
    }

    public boolean isEnded() {
        return ended;
    }

    public String getEndReason() {
        return endReason;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public UUID getPairId() { return pairId; }
    public String getSessionIdA() { return sessionIdA; }
    public UUID getUserIdA() { return userIdA; }
    public String getSessionIdB() { return sessionIdB; }
    public UUID getUserIdB() { return userIdB; }
    public UUID getConversationId() { return conversationId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "ChatPair{pairId=" + pairId +
                ", sessionA=" + sessionIdA +
                ", sessionB=" + sessionIdB +
                ", ended=" + ended + '}';
    }
}
