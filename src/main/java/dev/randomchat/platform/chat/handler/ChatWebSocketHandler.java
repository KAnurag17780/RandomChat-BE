package dev.randomchat.platform.chat.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.randomchat.platform.chat.matchmaking.ChatPair;
import dev.randomchat.platform.chat.matchmaking.MatchmakingEngine;
import dev.randomchat.platform.chat.protocol.ClientAction;
import dev.randomchat.platform.chat.protocol.ServerEvent;
import dev.randomchat.platform.chat.session.ChatSession;
import dev.randomchat.platform.chat.session.SessionRegistry;
import dev.randomchat.platform.conversation.entity.MessageEntity;
import dev.randomchat.platform.conversation.service.ConversationService;
import dev.randomchat.platform.user.entity.UserProfileEntity;
import dev.randomchat.platform.user.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Primary WebSocket handler for the /ws/chat endpoint.
 * <p>
 * Processes client actions: FIND_MATCH, SEND, NEXT, LEAVE, TYPING, PING.<br>
 * All matchmaking state transitions are delegated to
 * {@link MatchmakingEngine}.<br>
 * All message delivery uses {@link WebSocketSender}.
 * <p>
 * <strong>Threading model:</strong>
 * <ul>
 * <li>afterConnectionEstablished, handleTextMessage, afterConnectionClosed are
 * called
 * by the WebSocket container thread pool.</li>
 * <li>Matchmaking state transitions happen under the engine's internal
 * lock.</li>
 * <li>Database I/O and WebSocket sends happen after releasing the lock.</li>
 * </ul>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    @Value("${app.websocket.send-time-limit:10000}")
    private int sendTimeLimit;

    @Value("${app.websocket.send-buffer-size:524288}")
    private int sendBufferSize;

    private final SessionRegistry sessionRegistry;
    private final MatchmakingEngine matchmakingEngine;
    private final ConversationService conversationService;
    private final UserProfileRepository profileRepository;
    private final WebSocketSender sender;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(SessionRegistry sessionRegistry,
            MatchmakingEngine matchmakingEngine,
            ConversationService conversationService,
            UserProfileRepository profileRepository,
            WebSocketSender sender,
            ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.matchmakingEngine = matchmakingEngine;
        this.conversationService = conversationService;
        this.profileRepository = profileRepository;
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession rawSession) {
        Principal principal = rawSession.getPrincipal();
        if (principal == null) {
            log.warn("Unauthenticated WebSocket connection rejected: {}", rawSession.getId());
            silentClose(rawSession, CloseStatus.POLICY_VIOLATION);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid principal format: {}", principal.getName());
            silentClose(rawSession, CloseStatus.POLICY_VIOLATION);
            return;
        }

        ChatSession session = new ChatSession(rawSession, userId, sendTimeLimit, sendBufferSize);
        sessionRegistry.register(session);
        log.info("WebSocket connected: sessionId={}, userId={}", session.getSessionId(), userId);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession rawSession,
            @NonNull CloseStatus status) {
        Optional<ChatSession> sessionOpt = sessionRegistry.findById(rawSession.getId());
        if (sessionOpt.isEmpty())
            return;

        ChatSession session = sessionOpt.get();
        log.info("WebSocket closed: sessionId={}, userId={}, status={}",
                session.getSessionId(), session.getUserId(), status);

        // End pair and notify partner if matched/waiting
        Optional<MatchmakingEngine.LeaveResult> leaveResult = matchmakingEngine.leaveOrDisconnect(session);

        leaveResult.ifPresent(result -> {
            // Persist conversation end (outside lock)
            if (result.endedPair() != null) {
                conversationService.endConversation(
                        result.endedPair().getConversationId(), "DISCONNECT");
            }
            // Notify partner
            if (result.partner() != null) {
                sender.sendEvent(result.partner(), ServerEvent.partnerLeft("DISCONNECT"));
            }
        });

        // Final cleanup
        matchmakingEngine.finalizeDisconnect(session);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session,
            @NonNull Throwable exception) {
        log.warn("WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
        // afterConnectionClosed will be called next; cleanup happens there
    }

    // -----------------------------------------------------------------------
    // Message dispatch
    // -----------------------------------------------------------------------

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession rawSession,
            @NonNull TextMessage message) {
        Optional<ChatSession> sessionOpt = sessionRegistry.findById(rawSession.getId());
        if (sessionOpt.isEmpty()) {
            log.warn("Received message from unregistered session {}", rawSession.getId());
            return;
        }

        ChatSession session = sessionOpt.get();
        session.updateHeartbeat();

        ClientAction action;
        try {
            action = objectMapper.readValue(message.getPayload(), ClientAction.class);
        } catch (JsonProcessingException e) {
            sender.sendError(session, "Invalid JSON payload");
            return;
        }

        if (action.getAction() == null) {
            sender.sendError(session, "Missing 'action' field");
            return;
        }

        switch (action.getAction()) {
            case ClientAction.FIND_MATCH -> handleFindMatch(session);
            case ClientAction.SEND -> handleSend(session, action);
            case ClientAction.NEXT -> handleNext(session);
            case ClientAction.LEAVE -> handleLeave(session);
            case ClientAction.TYPING -> handleTyping(session, action);
            case ClientAction.PING -> sender.sendEvent(session, ServerEvent.pong());
            default -> sender.sendError(session, "Unknown action: " + action.getAction());
        }
    }

    // -----------------------------------------------------------------------
    // Action handlers
    // -----------------------------------------------------------------------

    private void handleFindMatch(ChatSession session) {
        // Generate conversation ID before entering the matchmaking lock
        UUID conversationId = UUID.randomUUID();

        MatchmakingEngine.MatchResult result = matchmakingEngine.findMatch(session, conversationId);

        if (!result.matched()) {
            // Tell client we're searching
            sender.sendEvent(session, ServerEvent.searching());
            return;
        }

        ChatPair pair = result.pair();

        // Persist conversation OUTSIDE the lock
        conversationService.createConversation(
                pair.getConversationId(), pair.getUserIdA(), pair.getUserIdB());

        // Notify both participants
        ServerEvent.PartnerInfo partnerOfA = buildPartnerInfo(pair.getUserIdB());
        ServerEvent.PartnerInfo partnerOfB = buildPartnerInfo(pair.getUserIdA());

        sender.sendEvent(result.self(),
                ServerEvent.matched(pair.getPairId(), pair.getConversationId(), partnerOfA));
        sender.sendEvent(result.partner(),
                ServerEvent.matched(pair.getPairId(), pair.getConversationId(), partnerOfB));
    }

    private void handleSend(ChatSession session, ClientAction action) {
        // Rate limit: 30 messages per 10 seconds per session
        if (!session.getSendRateLimiter().tryAcquire()) {
            sender.sendError(session, "Rate limit exceeded. Please slow down.");
            return;
        }

        String text = action.getText();
        if (text == null || text.isBlank()) {
            sender.sendError(session, "Message text must not be blank");
            return;
        }
        if (text.length() > ClientAction.MAX_TEXT_LENGTH) {
            sender.sendError(session, "Message exceeds maximum length of " + ClientAction.MAX_TEXT_LENGTH);
            return;
        }

        Optional<ChatPair> pairOpt = matchmakingEngine.findPairBySession(session.getSessionId());
        if (pairOpt.isEmpty() || pairOpt.get().isEnded()) {
            sender.sendError(session, "You are not currently in a chat");
            return;
        }

        ChatPair pair = pairOpt.get();

        // Persist BEFORE acknowledging
        MessageEntity message;
        try {
            message = conversationService.persistMessage(
                    pair.getConversationId(),
                    session.getUserId(),
                    action.getMessageId(),
                    text);
        } catch (IllegalArgumentException e) {
            sender.sendError(session, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Failed to persist message for session {}: {}", session.getSessionId(), e.getMessage(), e);
            sender.sendError(session, "Failed to save message. Please try again.");
            return;
        }

        // Stale pair check: verify pair is still active AFTER persisting
        if (pair.isEnded()) {
            // Message persisted but partner already left — still ack to sender
            sender.sendEvent(session, ServerEvent.messageAck(message.getId(), action.getMessageId()));
            return;
        }

        // Relay to partner
        String partnerSessionId = pair.getPartnerSessionId(session.getSessionId());
        if (partnerSessionId != null) {
            sessionRegistry.findById(partnerSessionId).ifPresent(partnerSession -> sender.sendEvent(partnerSession,
                    ServerEvent.message(message.getId(), text, message.getCreatedAt())));
        }

        // Acknowledge to sender
        sender.sendEvent(session, ServerEvent.messageAck(message.getId(), action.getMessageId()));
    }

    private void handleNext(ChatSession session) {
        UUID newConversationId = UUID.randomUUID();
        Optional<MatchmakingEngine.NextResult> result = matchmakingEngine.next(session, newConversationId);

        if (result.isEmpty()) {
            sender.sendError(session, "You are not currently in a chat");
            return;
        }

        MatchmakingEngine.NextResult next = result.get();

        // Persist conversation end OUTSIDE the lock
        conversationService.endConversation(next.endedPair().getConversationId(), "NEXT");

        // Notify partner that this user left
        if (next.partner() != null) {
            sender.sendEvent(next.partner(), ServerEvent.partnerLeft("NEXT"));
        }

        // Tell self we're searching again
        sender.sendEvent(session, ServerEvent.searching());
    }

    private void handleLeave(ChatSession session) {
        Optional<MatchmakingEngine.LeaveResult> result = matchmakingEngine.leaveOrDisconnect(session);

        result.ifPresent(leave -> {
            if (leave.endedPair() != null) {
                conversationService.endConversation(leave.endedPair().getConversationId(), "LEAVE");
            }
            if (leave.partner() != null) {
                sender.sendEvent(leave.partner(), ServerEvent.partnerLeft("LEAVE"));
            }
        });

        // Final cleanup
        matchmakingEngine.finalizeDisconnect(session);
    }

    private void handleTyping(ChatSession session, ClientAction action) {
        boolean isTyping = Boolean.TRUE.equals(action.getIsTyping());

        Optional<ChatPair> pairOpt = matchmakingEngine.findPairBySession(session.getSessionId());
        if (pairOpt.isEmpty() || pairOpt.get().isEnded())
            return; // silently ignore if not paired

        String partnerSessionId = pairOpt.get().getPartnerSessionId(session.getSessionId());
        if (partnerSessionId != null) {
            sessionRegistry.findById(partnerSessionId)
                    .ifPresent(partnerSession -> sender.sendEvent(partnerSession, ServerEvent.typing(isTyping)));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ServerEvent.PartnerInfo buildPartnerInfo(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(p -> new ServerEvent.PartnerInfo(
                        userId,
                        p.getDisplayName() != null ? p.getDisplayName() : "Stranger",
                        p.getAvatarUrl(),
                        p.getBio(),
                        p.getInterests()))
                .orElse(new ServerEvent.PartnerInfo(userId, "Stranger", null, null, null));
    }

    private void silentClose(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("Failed to close session {}: {}", session.getId(), e.getMessage());
        }
    }
}
