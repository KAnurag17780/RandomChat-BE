package dev.randomchat.platform.chat.handler;

import dev.randomchat.platform.chat.matchmaking.MatchmakingEngine;
import dev.randomchat.platform.chat.session.ChatSession;
import dev.randomchat.platform.chat.session.SessionRegistry;
import dev.randomchat.platform.chat.session.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodically checks for idle (heartbeat-timed-out) WebSocket sessions
 * and terminates them, triggering the normal {@code afterConnectionClosed} cleanup path.
 * <p>
 * Runs every {@code heartbeat.interval-seconds} seconds.
 */
@Component
@EnableScheduling
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    @Value("${app.heartbeat.timeout-seconds:90}")
    private long timeoutSeconds;

    private final SessionRegistry sessionRegistry;
    private final MatchmakingEngine matchmakingEngine;
    private final WebSocketSender sender;

    public HeartbeatScheduler(SessionRegistry sessionRegistry,
                               MatchmakingEngine matchmakingEngine,
                               WebSocketSender sender) {
        this.sessionRegistry = sessionRegistry;
        this.matchmakingEngine = matchmakingEngine;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${app.heartbeat.interval-seconds:30}000")
    public void checkIdleSessions() {
        Instant cutoff = Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS);
        List<ChatSession> stale = new ArrayList<>();

        for (ChatSession session : sessionRegistry.allSessions()) {
            if (session.getState() == UserState.DISCONNECTED ||
                    session.getState() == UserState.DISCONNECTING) continue;

            if (session.getLastHeartbeatAt().isBefore(cutoff)) {
                stale.add(session);
            }
        }

        for (ChatSession session : stale) {
            log.info("Closing idle session: sessionId={}, userId={}, lastHeartbeat={}",
                    session.getSessionId(), session.getUserId(), session.getLastHeartbeatAt());
            try {
                session.getDecoratedSession().close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (Exception e) {
                log.debug("Error closing idle session {}: {}", session.getSessionId(), e.getMessage());
            }
        }

        if (!stale.isEmpty()) {
            log.info("Heartbeat sweep: closed {} idle sessions", stale.size());
        }
    }
}
