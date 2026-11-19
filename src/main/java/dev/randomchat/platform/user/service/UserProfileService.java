package dev.randomchat.platform.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.randomchat.platform.auth.service.AuthService;
import dev.randomchat.platform.common.exception.ResourceNotFoundException;
import dev.randomchat.platform.user.dto.UpdateProfileRequest;
import dev.randomchat.platform.user.dto.UserProfileDTO;
import dev.randomchat.platform.user.entity.UserProfileEntity;
import dev.randomchat.platform.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for reading and updating user profiles.
 * <p>
 * Only fields explicitly permitted for public viewing are exposed via DTOs.
 * Private account data (email, password) is never included.
 */
@Service
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserProfileRepository profileRepository, 
                              AuthService authService,
                              ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.authService = authService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Returns the authenticated user's own profile.
     */
    @Transactional(readOnly = true)
    public UserProfileDTO getMyProfile() {
        UUID userId = authService.getAuthenticatedUserId();
        UserProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId.toString()));
        return toDTO(profile);
    }

    /**
     * Updates the authenticated user's own profile fields.
     * Username is immutable after registration.
     */
    @Transactional
    public UserProfileDTO updateMyProfile(UpdateProfileRequest request) {
        UUID userId = authService.getAuthenticatedUserId();
        UserProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId.toString()));

        if (request.displayName() != null) {
            profile.setDisplayName(request.displayName());
        }
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl());
        }
        if (request.interests() != null) {
            profile.setInterests(normalizeInterests(request.interests()));
        }

        profileRepository.save(profile);
        return toDTO(profile);
    }

    private String normalizeInterests(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            // Check if already valid JSON (e.g. ["Books"], {"topic":"Books"}, or "\"Books\"")
            objectMapper.readTree(trimmed);
            return trimmed;
        } catch (Exception e) {
            // Convert comma-separated string or plain word (e.g. "Books" -> ["Books"])
            String[] parts = trimmed.split(",");
            List<String> list = Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            try {
                return objectMapper.writeValueAsString(list);
            } catch (Exception ex) {
                return "[\"" + trimmed.replace("\"", "\\\"") + "\"]";
            }
        }
    }

    /**
     * Returns a public profile by username.
     * Only exposes fields safe for authenticated strangers to see.
     */
    @Transactional(readOnly = true)
    public UserProfileDTO getPublicProfile(String username) {
        UserProfileEntity profile = profileRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", username));
        // Return a minimal public view — no internal IDs exposed
        return new UserProfileDTO(
                null,          // profileId hidden from strangers
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                null,          // interests hidden from strangers by default
                null,          // createdAt hidden from strangers
                null           // updatedAt hidden from strangers
        );
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private UserProfileDTO toDTO(UserProfileEntity p) {
        return new UserProfileDTO(
                p.getId(),
                p.getUsername(),
                p.getDisplayName(),
                p.getBio(),
                p.getAvatarUrl(),
                p.getInterests(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
