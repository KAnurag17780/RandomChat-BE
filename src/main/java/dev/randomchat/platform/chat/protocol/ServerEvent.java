package dev.randomchat.platform.chat.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound server-to-client WebSocket event envelope.
 * <p>
 * {@code type} is always present. Other fields are populated depending on the event type.
 * {@code null} fields are excluded from JSON serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerEvent {

    // --- Event types ---
    public static final String SEARCHING      = "SEARCHING";
    public static final String MATCHED        = "MATCHED";
    public static final String MESSAGE        = "MESSAGE";
    public static final String MESSAGE_ACK    = "MESSAGE_ACK";
    public static final String PARTNER_LEFT   = "PARTNER_LEFT";
    public static final String TYPING         = "TYPING";
    public static final String ERROR          = "ERROR";
    public static final String PONG           = "PONG";

    private String type;

    // MATCHED fields
    private UUID pairId;
    private UUID conversationId;
    private PartnerInfo partner;

    // MESSAGE fields
    private UUID messageId;
    private String text;
    private Instant timestamp;

    // MESSAGE_ACK fields
    private UUID clientMessageId;

    // PARTNER_LEFT / ERROR fields
    private String reason;
    private String message;

    // TYPING fields
    private Boolean isTyping;

    // -----------------------------------------------------------------------
    // Static factory methods
    // -----------------------------------------------------------------------

    public static ServerEvent searching() {
        ServerEvent e = new ServerEvent();
        e.type = SEARCHING;
        return e;
    }

    public static ServerEvent matched(UUID pairId, UUID conversationId, PartnerInfo partner) {
        ServerEvent e = new ServerEvent();
        e.type = MATCHED;
        e.pairId = pairId;
        e.conversationId = conversationId;
        e.partner = partner;
        return e;
    }

    public static ServerEvent message(UUID messageId, String text, Instant timestamp) {
        ServerEvent e = new ServerEvent();
        e.type = MESSAGE;
        e.messageId = messageId;
        e.text = text;
        e.timestamp = timestamp;
        return e;
    }

    public static ServerEvent messageAck(UUID messageId, UUID clientMessageId) {
        ServerEvent e = new ServerEvent();
        e.type = MESSAGE_ACK;
        e.messageId = messageId;
        e.clientMessageId = clientMessageId;
        return e;
    }

    public static ServerEvent partnerLeft(String reason) {
        ServerEvent e = new ServerEvent();
        e.type = PARTNER_LEFT;
        e.reason = reason;
        return e;
    }

    public static ServerEvent typing(boolean isTyping) {
        ServerEvent e = new ServerEvent();
        e.type = TYPING;
        e.isTyping = isTyping;
        return e;
    }

    public static ServerEvent error(String message) {
        ServerEvent e = new ServerEvent();
        e.type = ERROR;
        e.message = message;
        return e;
    }

    public static ServerEvent pong() {
        ServerEvent e = new ServerEvent();
        e.type = PONG;
        return e;
    }

    // -----------------------------------------------------------------------
    // Nested DTO
    // -----------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartnerInfo(String displayName, String avatarUrl) {}

    // -----------------------------------------------------------------------
    // Getters (Jackson serialization)
    // -----------------------------------------------------------------------

    public String getType() { return type; }
    public UUID getPairId() { return pairId; }
    public UUID getConversationId() { return conversationId; }
    public PartnerInfo getPartner() { return partner; }
    public UUID getMessageId() { return messageId; }
    public String getText() { return text; }
    public Instant getTimestamp() { return timestamp; }
    public UUID getClientMessageId() { return clientMessageId; }
    public String getReason() { return reason; }
    public String getMessage() { return message; }
    public Boolean getIsTyping() { return isTyping; }
}
