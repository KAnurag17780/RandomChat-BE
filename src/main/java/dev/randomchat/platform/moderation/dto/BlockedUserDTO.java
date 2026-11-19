package dev.randomchat.platform.moderation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-safe DTO representing a blocked user in the current user's block list.
 */
public record BlockedUserDTO(UUID blockedUserId, Instant blockedAt) {}
