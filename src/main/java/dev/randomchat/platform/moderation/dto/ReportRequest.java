package dev.randomchat.platform.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for submitting a user report.
 */
public record ReportRequest(

        @NotNull(message = "reportedUserId is required")
        UUID reportedUserId,

        UUID conversationId,  // optional – references the conversation context

        @NotBlank(message = "reason is required")
        @Size(max = 50, message = "reason must not exceed 50 characters")
        String reason,

        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description
) {}
