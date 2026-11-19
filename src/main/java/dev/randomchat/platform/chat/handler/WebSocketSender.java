package dev.randomchat.platform.chat.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.randomchat.platform.chat.protocol.ServerEvent;
import dev.randomchat.platform.chat.session.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * Utility for safely sending {@link ServerEvent} JSON frames to a WebSocket session.
 * <p>
 * Uses the {@link org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator}
 * inside {@link ChatSession} so concurrent sends from multiple threads are serialised
 * by the decorator's internal lock rather than the caller's thread.
 * <p>
 * A failed send is logged but does not throw, to avoid propagating send failures
 * into state-management code paths. Callers that need to know whether a send
 * succeeded should check the return value.
 */
@Component
public class WebSocketSender {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSender.class);

    private final ObjectMapper objectMapper;

    public WebSocketSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes {@code event} to JSON and sends it to {@code session}.
     *
     * @return {@code true} if sent without exception, {@code false} otherwise
     */
    public boolean sendEvent(ChatSession session, ServerEvent event) {
        if (!session.isOpen()) {
            log.debug("Skipping send to closed session {}", session.getSessionId());
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            session.getDecoratedSession().sendMessage(new TextMessage(json));
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {} for session {}: {}",
                    event.getType(), session.getSessionId(), e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to send event {} to session {}: {}",
                    event.getType(), session.getSessionId(), e.getMessage());
        }
        return false;
    }

    /**
     * Sends an error message without throwing.
     */
    public void sendError(ChatSession session, String message) {
        sendEvent(session, ServerEvent.error(message));
    }
}
