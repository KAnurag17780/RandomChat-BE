package dev.randomchat.platform.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.randomchat.platform.chat.matchmaking.ChatPair;
import dev.randomchat.platform.chat.matchmaking.MatchmakingEngine;
import dev.randomchat.platform.chat.session.ChatSession;
import dev.randomchat.platform.chat.session.SessionRegistry;
import dev.randomchat.platform.chat.session.UserState;
import dev.randomchat.platform.user.repository.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Concurrency and protocol invariant tests for the MatchmakingEngine.
 * <p>
 * These tests verify the 14 scenarios listed in PLAN.md § 13.3 / 13.4
 * that can be exercised without a running HTTP server or database.
 * <p>
 * Scenarios covered:
 * <ol>
 *   <li>Two authenticated users connect and match</li>
 *   <li>A third user waits when two are already paired</li>
 *   <li>Unmatched users cannot send (not tested here — handler-level)</li>
 *   <li>Alice presses NEXT: Bob becomes idle, Alice re-queued</li>
 *   <li>Alice can be matched again after NEXT</li>
 *   <li>Both participants press NEXT simultaneously (idempotent)</li>
 *   <li>A user disconnects while waiting — removed from queue</li>
 *   <li>A user disconnects while matched — pair ended, partner notified</li>
 *   <li>Duplicate FIND_MATCH does not create duplicate queue entries</li>
 *   <li>Stale pair cannot relay messages after it is ended</li>
 *   <li>Same account (two connections) cannot self-match</li>
 *   <li>Block relationship prevents pairing</li>
 *   <li>Cleanup is idempotent (repeated leave/disconnect)</li>
 *   <li>Pair registry and session registry are symmetric after cleanup</li>
 * </ol>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatProtocolInvariantTest {

    @Mock
    private UserBlockRepository blockRepository;

    private SessionRegistry registry;
    private MatchmakingEngine engine;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
        engine = new MatchmakingEngine(registry, blockRepository);
        when(blockRepository.existsByBlockerIdAndBlockedUserId(any(), any())).thenReturn(false);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ChatSession session(String id) {
        return session(id, UUID.randomUUID());
    }

    private ChatSession session(String id, UUID userId) {
        WebSocketSession raw = mock(WebSocketSession.class);
        when(raw.getId()).thenReturn(id);
        when(raw.isOpen()).thenReturn(true);
        ChatSession s = new ChatSession(raw, userId, 10_000, 524_288);
        registry.register(s);
        return s;
    }

    // -----------------------------------------------------------------------
    // Scenario 1: Two users connect and match
    // -----------------------------------------------------------------------

    @Test
    void scenario1_twoUsers_connectAndMatch() {
        ChatSession alice = session("alice");
        ChatSession bob = session("bob");

        var r1 = engine.findMatch(alice, UUID.randomUUID());
        assertThat(r1.matched()).isFalse();
        assertThat(alice.getState()).isEqualTo(UserState.WAITING);

        var r2 = engine.findMatch(bob, UUID.randomUUID());
        assertThat(r2.matched()).isTrue();
        assertThat(r2.pair()).isNotNull();
        assertThat(alice.getState()).isEqualTo(UserState.MATCHED);
        assertThat(bob.getState()).isEqualTo(UserState.MATCHED);

        // Both should see each other in the pair
        ChatPair pair = r2.pair();
        assertThat(pair.getUserIdA()).isNotEqualTo(pair.getUserIdB());
        assertThat(pair.isEnded()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Scenario 2: Third user waits when two are already paired
    // -----------------------------------------------------------------------

    @Test
    void scenario2_thirdUser_waitsAfterPairForms() {
        ChatSession alice = session("alice2");
        ChatSession bob = session("bob2");
        ChatSession carol = session("carol2");

        engine.findMatch(alice, UUID.randomUUID());
        engine.findMatch(bob, UUID.randomUUID()); // alice + bob paired

        var r = engine.findMatch(carol, UUID.randomUUID());
        assertThat(r.matched()).isFalse();
        assertThat(carol.getState()).isEqualTo(UserState.WAITING);
        assertThat(engine.getQueueSize()).isEqualTo(1); // only carol waiting
    }

    // -----------------------------------------------------------------------
    // Scenario 4: Alice presses NEXT — Bob becomes idle, Alice re-queued
    // -----------------------------------------------------------------------

    @Test
    void scenario4_aliceNext_bobIdleAliceRequeued() {
        ChatSession alice = session("alice4");
        ChatSession bob = session("bob4");

        engine.findMatch(alice, UUID.randomUUID());
        var match = engine.findMatch(bob, UUID.randomUUID());
        assertThat(match.matched()).isTrue();

        var next = engine.next(alice, UUID.randomUUID());
        assertThat(next).isPresent();
        assertThat(next.get().endedPair().isEnded()).isTrue();
        assertThat(next.get().endedPair().getEndReason()).isEqualTo("NEXT");

        assertThat(alice.getState()).isEqualTo(UserState.WAITING);
        assertThat(bob.getState()).isEqualTo(UserState.CONNECTED); // idle, not waiting
        assertThat(engine.getQueueSize()).isEqualTo(1); // only alice re-queued
    }

    // -----------------------------------------------------------------------
    // Scenario 5: Alice can be matched again after NEXT
    // -----------------------------------------------------------------------

    @Test
    void scenario5_aliceCanMatchAgainAfterNext() {
        ChatSession alice = session("alice5");
        ChatSession bob = session("bob5");
        ChatSession carol = session("carol5");

        engine.findMatch(alice, UUID.randomUUID());
        engine.findMatch(bob, UUID.randomUUID()); // alice + bob paired

        engine.next(alice, UUID.randomUUID()); // alice re-queued

        // Carol connects → should match with alice
        var r = engine.findMatch(carol, UUID.randomUUID());
        assertThat(r.matched()).isTrue();
        assertThat(alice.getState()).isEqualTo(UserState.MATCHED);
        assertThat(carol.getState()).isEqualTo(UserState.MATCHED);
    }

    // -----------------------------------------------------------------------
    // Scenario 6: Both press NEXT simultaneously — state stays consistent
    // -----------------------------------------------------------------------

    @Test
    void scenario6_bothPressNext_idempotentCleanup() {
        ChatSession alice = session("alice6");
        ChatSession bob = session("bob6");

        engine.findMatch(alice, UUID.randomUUID());
        engine.findMatch(bob, UUID.randomUUID());

        // Alice goes NEXT first
        var nextAlice = engine.next(alice, UUID.randomUUID());
        assertThat(nextAlice).isPresent();

        // Bob then tries NEXT on an already-ended pair — should be handled safely
        var nextBob = engine.next(bob, UUID.randomUUID());
        assertThat(nextBob).isEmpty(); // bob was no longer in a pair
    }

    // -----------------------------------------------------------------------
    // Scenario 7: User disconnects while waiting — removed from queue
    // -----------------------------------------------------------------------

    @Test
    void scenario7_disconnectWhileWaiting_removedFromQueue() {
        ChatSession alice = session("alice7");

        engine.findMatch(alice, UUID.randomUUID());
        assertThat(engine.getQueueSize()).isEqualTo(1);

        engine.leaveOrDisconnect(alice);
        engine.finalizeDisconnect(alice);

        assertThat(engine.getQueueSize()).isEqualTo(0);
        assertThat(alice.getState()).isEqualTo(UserState.DISCONNECTED);
    }

    // -----------------------------------------------------------------------
    // Scenario 8: User disconnects while matched — pair ended
    // -----------------------------------------------------------------------

    @Test
    void scenario8_disconnectWhileMatched_pairEnded() {
        ChatSession alice = session("alice8");
        ChatSession bob = session("bob8");

        engine.findMatch(alice, UUID.randomUUID());
        var match = engine.findMatch(bob, UUID.randomUUID());
        ChatPair pair = match.pair();

        var leave = engine.leaveOrDisconnect(alice);

        assertThat(leave).isPresent();
        assertThat(pair.isEnded()).isTrue();
        assertThat(bob.getState()).isEqualTo(UserState.CONNECTED); // bob returned to idle
    }

    // -----------------------------------------------------------------------
    // Scenario 9: Duplicate FIND_MATCH — no duplicate queue entry
    // -----------------------------------------------------------------------

    @Test
    void scenario9_duplicateFindMatch_noQueueDuplicate() {
        ChatSession alice = session("alice9");

        engine.findMatch(alice, UUID.randomUUID()); // first, alice goes WAITING
        engine.findMatch(alice, UUID.randomUUID()); // duplicate — should be rejected

        assertThat(engine.getQueueSize()).isEqualTo(1);
        assertThat(alice.getState()).isEqualTo(UserState.WAITING);
    }

    // -----------------------------------------------------------------------
    // Scenario 10: Stale pair cannot relay messages after ended
    // -----------------------------------------------------------------------

    @Test
    void scenario10_staleMessages_cannotReachNewPartner() {
        ChatSession alice = session("alice10");
        ChatSession bob = session("bob10");

        engine.findMatch(alice, UUID.randomUUID());
        var match = engine.findMatch(bob, UUID.randomUUID());
        ChatPair oldPair = match.pair();

        // Alice leaves — pair ended
        engine.leaveOrDisconnect(alice);

        // Old pair is ended
        assertThat(oldPair.isEnded()).isTrue();

        // findPairBySession for alice's old session should return empty
        assertThat(engine.findPairBySession(alice.getSessionId())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 11: Same account, two connections — no self-match
    // -----------------------------------------------------------------------

    @Test
    void scenario11_sameUser_twoConnections_noSelfMatch() {
        UUID sharedId = UUID.randomUUID();
        ChatSession conn1 = session("conn1-shared", sharedId);
        ChatSession conn2 = session("conn2-shared", sharedId);

        engine.findMatch(conn1, UUID.randomUUID());
        var r = engine.findMatch(conn2, UUID.randomUUID());

        assertThat(r.matched()).isFalse();
        assertThat(engine.getQueueSize()).isEqualTo(2); // both waiting, no match
    }

    // -----------------------------------------------------------------------
    // Scenario 12: Block relationship prevents pairing
    // -----------------------------------------------------------------------

    @Test
    void scenario12_blockedUsers_notPaired() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();

        // Alice blocked Bob
        when(blockRepository.existsByBlockerIdAndBlockedUserId(aliceId, bobId)).thenReturn(true);

        ChatSession alice = session("alice12", aliceId);
        ChatSession bob = session("bob12", bobId);

        engine.findMatch(alice, UUID.randomUUID());
        var r = engine.findMatch(bob, UUID.randomUUID());

        assertThat(r.matched()).isFalse();
        assertThat(engine.getQueueSize()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Scenario 13: Cleanup is idempotent
    // -----------------------------------------------------------------------

    @Test
    void scenario13_cleanup_idempotent() {
        ChatSession alice = session("alice13");

        engine.findMatch(alice, UUID.randomUUID());

        // Call leave twice
        engine.leaveOrDisconnect(alice);
        engine.leaveOrDisconnect(alice); // second call must not throw or corrupt state

        engine.finalizeDisconnect(alice);
        assertThat(alice.getState()).isEqualTo(UserState.DISCONNECTED);
        assertThat(engine.getQueueSize()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Scenario 14: Pair registry and session registry symmetric after cleanup
    // -----------------------------------------------------------------------

    @Test
    void scenario14_registryConsistency_afterFullCycle() {
        ChatSession alice = session("alice14");
        ChatSession bob = session("bob14");

        engine.findMatch(alice, UUID.randomUUID());
        var match = engine.findMatch(bob, UUID.randomUUID());
        ChatPair pair = match.pair();

        // Simulate full lifecycle
        engine.leaveOrDisconnect(alice);
        engine.finalizeDisconnect(alice);

        engine.leaveOrDisconnect(bob);
        engine.finalizeDisconnect(bob);

        // After both disconnect: no active pairs, no stale pair references
        assertThat(engine.findPairBySession(alice.getSessionId())).isEmpty();
        assertThat(engine.findPairBySession(bob.getSessionId())).isEmpty();
        assertThat(engine.getQueueSize()).isEqualTo(0);
        assertThat(pair.isEnded()).isTrue();
    }
}
