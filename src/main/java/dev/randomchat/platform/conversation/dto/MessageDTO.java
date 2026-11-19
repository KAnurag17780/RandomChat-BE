package dev.randomchat.platform.conversation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a single message in the history API.
 */
public record MessageDTO(
        UUID messageId,
        UUID senderId,
        String content,
        String messageType,
        Instant createdAt
) {}
