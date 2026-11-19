package dev.randomchat.platform.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-safe representation of a user profile.
 * Never exposes email, password, or internal account fields.
 */
public record UserProfileDTO(
        UUID profileId,
        String username,
        String displayName,
        String bio,
        String avatarUrl,
        String interests,
        Instant createdAt,
        Instant updatedAt
) {}
