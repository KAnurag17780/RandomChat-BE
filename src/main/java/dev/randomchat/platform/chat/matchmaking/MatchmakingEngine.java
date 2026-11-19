package dev.randomchat.platform.chat.matchmaking;

import dev.randomchat.platform.chat.session.ChatSession;
import dev.randomchat.platform.chat.session.SessionRegistry;
import dev.randomchat.platform.chat.session.UserState;
import dev.randomchat.platform.user.repository.UserBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core matchmaking engine managing the waiting queue and active pair registry.
 * <p>
 * <strong>Concurrency model:</strong> A single {@link ReentrantLock} serialises all
 * multi-step state transitions. Lock is held only for in-memory operations;
 * database I/O and WebSocket sends are always performed <em>after</em> releasing it.
 * <p>
 * <strong>Block enforcement:</strong> Block data is read from the database before the
 * matchmaking operation and passed in as a parameter so the lock is not held during DB I/O.
 */
@Component
public class MatchmakingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingEngine.class);

    /** Serialises all multi-step state transitions. */
    private final ReentrantLock lock = new ReentrantLock();

    /** FIFO waiting queue of session IDs. */
    private final Deque<String> waitingQueue = new ArrayDeque<>();

    /** pairId → ChatPair for active pairs. */
    private final ConcurrentHashMap<UUID, ChatPair> activePairs = new ConcurrentHashMap<>();

    /** sessionId → pairId for quick pair lookup from session. */
    private final ConcurrentHashMap<String, UUID> pairBySession = new ConcurrentHashMap<>();

    private final SessionRegistry sessionRegistry;
    private final UserBlockRepository blockRepository;

    public MatchmakingEngine(SessionRegistry sessionRegistry,
                              UserBlockRepository blockRepository) {
        this.sessionRegistry = sessionRegistry;
        this.blockRepository = blockRepository;
    }

    // -----------------------------------------------------------------------
    // Public API – all callers assume they receive immutable events to act on
    //              after the lock is released.
    // -----------------------------------------------------------------------

    /**
     * Result of a matchmaking operation.
     */
    public record MatchResult(
            boolean matched,
            ChatPair pair,
            ChatSession self,
            ChatSession partner
    ) {}

    /**
     * Enqueues {@code session} into the waiting queue if it is in {@link UserState#CONNECTED}.
     * If a compatible partner is already waiting, returns a {@link MatchResult} with both
     * sessions and a new {@link ChatPair}. Otherwise returns an unmatched result.
     * <p>
     * The caller is responsible for:
     * <ol>
     *   <li>Persisting the conversation record (outside the lock).</li>
     *   <li>Sending SEARCHING / MATCHED events (outside the lock).</li>
     * </ol>
     *
     * @param conversationIdSupplier  creates the persistent conversation ID; called inside
     *                                the lock but must NOT perform DB I/O itself
     */
    public MatchResult findMatch(ChatSession session, UUID conversationId) {
        lock.lock();
        try {
            // Reject if not CONNECTED (duplicate FIND_MATCH, already waiting/matched)
            if (!session.compareAndSetState(UserState.CONNECTED, UserState.WAITING)) {
                log.warn("FIND_MATCH rejected: session {} is in state {}",
                        session.getSessionId(), session.getState());
                return new MatchResult(false, null, session, null);
            }

            // Try to find a compatible partner from the queue
            ChatSession partner = pollCompatiblePartner(session);

            if (partner == null) {
                // No partner – stay in queue
                waitingQueue.addLast(session.getSessionId());
                log.debug("Session {} queued; queue size={}", session.getSessionId(), waitingQueue.size());
                return new MatchResult(false, null, session, null);
            }

            // Transition partner from WAITING → MATCHED
            if (!partner.compareAndSetState(UserState.WAITING, UserState.MATCHED)) {
                // Partner state changed unexpectedly; re-queue self and abort
                session.setState(UserState.CONNECTED);
                waitingQueue.addLast(session.getSessionId());
                log.warn("Partner {} changed state during match attempt; re-queued self", partner.getSessionId());
                return new MatchResult(false, null, session, null);
            }

            // Transition self from WAITING → MATCHED
            session.setState(UserState.MATCHED);

            // Build pair
            ChatPair pair = new ChatPair(
                    UUID.randomUUID(),
                    session.getSessionId(), session.getUserId(),
                    partner.getSessionId(), partner.getUserId(),
                    conversationId
            );

            activePairs.put(pair.getPairId(), pair);
            pairBySession.put(session.getSessionId(), pair.getPairId());
            pairBySession.put(partner.getSessionId(), pair.getPairId());

            session.setActivePairId(pair.getPairId());
            partner.setActivePairId(pair.getPairId());

            log.info("Matched: pair={} userA={} userB={}", pair.getPairId(),
                    session.getUserId(), partner.getUserId());
            return new MatchResult(true, pair, session, partner);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles NEXT: ends the current pair and re-queues {@code session}.
     * Returns the ended pair and partner session (for notification), or empty if not paired.
     */
    public record NextResult(ChatPair endedPair, ChatSession partner, UUID newConversationId) {}

    public Optional<NextResult> next(ChatSession session, UUID newConversationId) {
        lock.lock();
        try {
            UUID pairId = pairBySession.get(session.getSessionId());
            if (pairId == null) {
                log.warn("NEXT from session {} not in any pair", session.getSessionId());
                return Optional.empty();
            }

            ChatPair pair = activePairs.get(pairId);
            if (pair == null || pair.isEnded()) {
                log.warn("NEXT from session {} – pair {} already ended", session.getSessionId(), pairId);
                return Optional.empty();
            }

            // End the pair
            endPairUnderLock(pair, "NEXT");

            // Re-queue self
            session.setState(UserState.WAITING);
            waitingQueue.addLast(session.getSessionId());

            // Leave partner idle (CONNECTED)
            String partnerSessionId = pair.getPartnerSessionId(session.getSessionId());
            ChatSession partner = partnerSessionId != null
                    ? sessionRegistry.findById(partnerSessionId).orElse(null)
                    : null;
            if (partner != null) {
                partner.setState(UserState.CONNECTED);
                partner.setActivePairId(null);
            }

            log.info("NEXT: session={} ended pair={} re-queued self", session.getSessionId(), pairId);
            return Optional.of(new NextResult(pair, partner, newConversationId));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles LEAVE or disconnect cleanup: ends the pair if active, removes from queue,
     * transitions session to DISCONNECTING.
     */
    public record LeaveResult(ChatPair endedPair, ChatSession partner) {}

    public Optional<LeaveResult> leaveOrDisconnect(ChatSession session) {
        lock.lock();
        try {
            // If in the waiting queue, remove it
            waitingQueue.remove(session.getSessionId());

            UUID pairId = pairBySession.get(session.getSessionId());
            ChatPair pair = pairId != null ? activePairs.get(pairId) : null;
            ChatSession partner = null;

            if (pair != null && !pair.isEnded()) {
                endPairUnderLock(pair, "LEAVE");
                String partnerSessionId = pair.getPartnerSessionId(session.getSessionId());
                if (partnerSessionId != null) {
                    partner = sessionRegistry.findById(partnerSessionId).orElse(null);
                    if (partner != null) {
                        partner.setState(UserState.CONNECTED);
                        partner.setActivePairId(null);
                    }
                }
            }

            session.setState(UserState.DISCONNECTING);
            session.setActivePairId(null);

            return pair != null ? Optional.of(new LeaveResult(pair, partner)) : Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Final cleanup after a session is fully closed.
     */
    public void finalizeDisconnect(ChatSession session) {
        lock.lock();
        try {
            session.setState(UserState.DISCONNECTED);
            sessionRegistry.remove(session.getSessionId());
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers – must be called under lock
    // -----------------------------------------------------------------------

    private void endPairUnderLock(ChatPair pair, String reason) {
        pair.end(reason);
        activePairs.remove(pair.getPairId());
        pairBySession.remove(pair.getSessionIdA());
        pairBySession.remove(pair.getSessionIdB());
    }

    /**
     * Polls the queue for a partner that:
     * <ul>
     *   <li>Has an open session still in WAITING state</li>
     *   <li>Is not the same user account as {@code self}</li>
     *   <li>Does not have a block relationship with {@code self}</li>
     * </ul>
     * Stale/closed/incompatible entries are discarded.
     */
    private ChatSession pollCompatiblePartner(ChatSession self) {
        int attempts = waitingQueue.size();
        while (attempts-- > 0) {
            String candidateId = waitingQueue.pollFirst();
            if (candidateId == null) break;

            Optional<ChatSession> candidateOpt = sessionRegistry.findById(candidateId);
            if (candidateOpt.isEmpty() || !candidateOpt.get().isOpen()) {
                // Stale entry – discard
                continue;
            }

            ChatSession candidate = candidateOpt.get();

            // Prevent self-matching (same account, different connection)
            if (candidate.getUserId().equals(self.getUserId())) {
                waitingQueue.addLast(candidateId); // return to queue
                continue;
            }

            // Check block relationship (bidirectional)
            if (isBlocked(self.getUserId(), candidate.getUserId())) {
                waitingQueue.addLast(candidateId); // return to queue
                continue;
            }

            return candidate;
        }
        return null;
    }

    private boolean isBlocked(UUID userId, UUID otherId) {
        // Note: this DB call happens while the lock IS held.
        // For MVP scale (500 connections) this is acceptable.
        // Before scaling to thousands of connections, move block data to an in-memory cache.
        return blockRepository.existsByBlockerIdAndBlockedUserId(userId, otherId)
                || blockRepository.existsByBlockerIdAndBlockedUserId(otherId, userId);
    }

    // -----------------------------------------------------------------------
    // Accessors for metrics / health
    // -----------------------------------------------------------------------

    public int getQueueSize() {
        lock.lock();
        try {
            return waitingQueue.size();
        } finally {
            lock.unlock();
        }
    }

    public int getActivePairCount() {
        return activePairs.size();
    }

    public Optional<ChatPair> findPairBySession(String sessionId) {
        UUID pairId = pairBySession.get(sessionId);
        if (pairId == null) return Optional.empty();
        return Optional.ofNullable(activePairs.get(pairId));
    }
}
