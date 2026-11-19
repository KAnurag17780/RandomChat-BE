package dev.randomchat.platform.chat;

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
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the MatchmakingEngine state machine.
 * Tests pairing logic, duplicate prevention, NEXT semantics, and disconnect cleanup.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchmakingEngineTest {

    @Mock
    private UserBlockRepository blockRepository;

    private SessionRegistry sessionRegistry;
    private MatchmakingEngine engine;

    @BeforeEach
    void setUp() {
        sessionRegistry = new SessionRegistry();
        engine = new MatchmakingEngine(sessionRegistry, blockRepository);

        // Default: no blocks
        when(blockRepository.existsByBlockerIdAndBlockedUserId(any(), any())).thenReturn(false);
    }

    private ChatSession makeSession(String sessionId, UUID userId) {
        WebSocketSession raw = mock(WebSocketSession.class);
        when(raw.getId()).thenReturn(sessionId);
        when(raw.isOpen()).thenReturn(true);
        // ConcurrentWebSocketSessionDecorator wraps raw session
        ChatSession session = new ChatSession(raw, userId, 10_000, 524_288);
        sessionRegistry.register(session);
        return session;
    }

    @Test
    void twoUsers_findMatch_areMatched() {
        ChatSession alice = makeSession("s-alice", UUID.randomUUID());
        ChatSession bob = makeSession("s-bob", UUID.randomUUID());

        // Alice searches first – no match
        MatchmakingEngine.MatchResult r1 = engine.findMatch(alice, UUID.randomUUID());
        assertThat(r1.matched()).isFalse();
        assertThat(alice.getState()).isEqualTo(UserState.WAITING);

        // Bob searches – matches with Alice
        MatchmakingEngine.MatchResult r2 = engine.findMatch(bob, UUID.randomUUID());
        assertThat(r2.matched()).isTrue();
        assertThat(r2.pair()).isNotNull();
        assertThat(alice.getState()).isEqualTo(UserState.MATCHED);
        assertThat(bob.getState()).isEqualTo(UserState.MATCHED);
    }

    @Test
    void duplicateFindMatch_doesNotCreateDuplicateQueueEntry() {
        ChatSession alice = makeSession("s-alice2", UUID.randomUUID());

        engine.findMatch(alice, UUID.randomUUID());
        assertThat(alice.getState()).isEqualTo(UserState.WAITING);
        assertThat(engine.getQueueSize()).isEqualTo(1);

        // Second FIND_MATCH should be rejected
        engine.findMatch(alice, UUID.randomUUID());
        assertThat(engine.getQueueSize()).isEqualTo(1);
    }

    @Test
    void sameUser_twoSessions_doNotSelfMatch() {
        UUID sharedUserId = UUID.randomUUID();
        ChatSession conn1 = makeSession("s-conn1", sharedUserId);
        ChatSession conn2 = makeSession("s-conn2", sharedUserId);

        engine.findMatch(conn1, UUID.randomUUID());
        MatchmakingEngine.MatchResult r = engine.findMatch(conn2, UUID.randomUUID());

        // conn2 should not match with conn1 (same user)
        assertThat(r.matched()).isFalse();
        assertThat(engine.getQueueSize()).isEqualTo(2);
    }

    @Test
    void blockedUser_doesNotMatch() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();

        // Alice blocked Bob
        when(blockRepository.existsByBlockerIdAndBlockedUserId(aliceId, bobId)).thenReturn(true);

        ChatSession alice = makeSession("s-alice3", aliceId);
        ChatSession bob = makeSession("s-bob3", bobId);

        engine.findMatch(alice, UUID.randomUUID());
        MatchmakingEngine.MatchResult r = engine.findMatch(bob, UUID.randomUUID());

        assertThat(r.matched()).isFalse();
        // Both should still be waiting
        assertThat(engine.getQueueSize()).isEqualTo(2);
    }

    @Test
    void next_endsCurrentPair_andRequeues() {
        ChatSession alice = makeSession("s-alice4", UUID.randomUUID());
        ChatSession bob = makeSession("s-bob4", UUID.randomUUID());

        engine.findMatch(alice, UUID.randomUUID());
        MatchmakingEngine.MatchResult matchResult = engine.findMatch(bob, UUID.randomUUID());
        assertThat(matchResult.matched()).isTrue();

        ChatPair pair = matchResult.pair();

        // Alice presses NEXT
        Optional<MatchmakingEngine.NextResult> nextResult = engine.next(alice, UUID.randomUUID());
        assertThat(nextResult).isPresent();
        assertThat(pair.isEnded()).isTrue();
        assertThat(pair.getEndReason()).isEqualTo("NEXT");

        // Alice re-queued, Bob idle
        assertThat(alice.getState()).isEqualTo(UserState.WAITING);
        assertThat(bob.getState()).isEqualTo(UserState.CONNECTED);
        assertThat(engine.getQueueSize()).isEqualTo(1);
    }

    @Test
    void leave_endsPair_andCleansUp() {
        ChatSession alice = makeSession("s-alice5", UUID.randomUUID());
        ChatSession bob = makeSession("s-bob5", UUID.randomUUID());

        engine.findMatch(alice, UUID.randomUUID());
        engine.findMatch(bob, UUID.randomUUID());

        Optional<MatchmakingEngine.LeaveResult> leaveResult = engine.leaveOrDisconnect(alice);
        assertThat(leaveResult).isPresent();
        assertThat(leaveResult.get().endedPair().isEnded()).isTrue();
        assertThat(bob.getState()).isEqualTo(UserState.CONNECTED);

        engine.finalizeDisconnect(alice);
        assertThat(alice.getState()).isEqualTo(UserState.DISCONNECTED);
    }

    @Test
    void disconnect_whileWaiting_removesFromQueue() {
        ChatSession alice = makeSession("s-alice6", UUID.randomUUID());

        engine.findMatch(alice, UUID.randomUUID());
        assertThat(engine.getQueueSize()).isEqualTo(1);

        engine.leaveOrDisconnect(alice);
        assertThat(engine.getQueueSize()).isEqualTo(0);
    }

    @Test
    void endedPair_cannotRelayMessages() {
        ChatSession alice = makeSession("s-alice7", UUID.randomUUID());
        ChatSession bob = makeSession("s-bob7", UUID.randomUUID());

        engine.findMatch(alice, UUID.randomUUID());
        MatchmakingEngine.MatchResult matchResult = engine.findMatch(bob, UUID.randomUUID());
        ChatPair pair = matchResult.pair();

        engine.leaveOrDisconnect(alice);

        // Pair must now be ended
        assertThat(pair.isEnded()).isTrue();

        // findPairBySession should return empty for alice's old session
        Optional<ChatPair> foundPair = engine.findPairBySession(alice.getSessionId());
        assertThat(foundPair).isEmpty();
    }

    @Test
    void cleanup_isIdempotent() {
        ChatSession alice = makeSession("s-alice8", UUID.randomUUID());

        engine.findMatch(alice, UUID.randomUUID());

        // Call leaveOrDisconnect twice – should not throw or corrupt state
        engine.leaveOrDisconnect(alice);
        engine.leaveOrDisconnect(alice);
        engine.finalizeDisconnect(alice);

        assertThat(alice.getState()).isEqualTo(UserState.DISCONNECTED);
    }
}
