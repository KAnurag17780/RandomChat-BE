package dev.randomchat.platform.chat.session;

/**
 * Lifecycle states for a WebSocket chat session.
 * <p>
 * Legal transitions:
 * <pre>
 *   CONNECTED → WAITING        (FIND_MATCH received)
 *   WAITING   → MATCHED        (matched with a partner)
 *   WAITING   → CONNECTED      (cancelled, timeout)
 *   MATCHED   → WAITING        (NEXT pressed, re-queuing self)
 *   MATCHED   → CONNECTED      (LEAVE or partner disconnect – not re-queuing)
 *   Any       → DISCONNECTING  (close event received)
 *   Any       → DISCONNECTED   (cleanup complete)
 * </pre>
 * No other transitions are legal. The matchmaking engine enforces this.
 */
public enum UserState {
    /** Socket is open, no active match, not waiting. */
    CONNECTED,
    /** In the matchmaking queue awaiting a partner. */
    WAITING,
    /** Paired with another user in an active chat session. */
    MATCHED,
    /** Close event received; cleanup in progress. */
    DISCONNECTING,
    /** Session fully cleaned up. Terminal state. */
    DISCONNECTED
}
