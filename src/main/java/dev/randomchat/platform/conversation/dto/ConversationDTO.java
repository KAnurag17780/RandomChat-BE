package dev.randomchat.platform.conversation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a conversation in the user's history list.
 */
public record ConversationDTO(
        UUID conversationId,
        String status,
        Instant startedAt,
        Instant endedAt,
        String endedReason
) {}
