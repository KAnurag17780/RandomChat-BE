package dev.randomchat.platform.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned after successful registration or login.
 * Never contains password or other sensitive credential data.
 */
public record AuthResponse(
        UUID userId,
        String email,
        String username,
        String displayName,
        Instant createdAt
) {}
