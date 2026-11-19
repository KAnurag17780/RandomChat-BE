package dev.randomchat.platform.chat.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Inbound client-to-server WebSocket action envelope.
 * <p>
 * {@code action} is the discriminator field. Additional fields are action-specific.
 * Unknown fields are ignored. Action names are validated in the handler.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientAction {

    // --- Action names ---
    public static final String FIND_MATCH = "FIND_MATCH";
    public static final String SEND       = "SEND";
    public static final String NEXT       = "NEXT";
    public static final String LEAVE      = "LEAVE";
    public static final String TYPING     = "TYPING";
    public static final String PING       = "PING";

    /** Maximum allowed text length in characters. */
    public static final int MAX_TEXT_LENGTH = 2000;

    private String action;

    // SEND fields
    private UUID messageId;   // client-generated idempotency key
    private String text;

    // TYPING fields
    private Boolean isTyping;

    // --- Getters / Setters (Jackson deserialization) ---

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Boolean getIsTyping() { return isTyping; }
    public void setIsTyping(Boolean isTyping) { this.isTyping = isTyping; }
}
