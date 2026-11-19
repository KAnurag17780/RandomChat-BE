package dev.randomchat.platform.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for updating the current user's profile.
 * Only updatable fields are included.
 */
public record UpdateProfileRequest(

        @Size(max = 50, message = "Display name must not exceed 50 characters")
        String displayName,

        @Size(max = 500, message = "Bio must not exceed 500 characters")
        String bio,

        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        String avatarUrl,

        String interests
) {}
