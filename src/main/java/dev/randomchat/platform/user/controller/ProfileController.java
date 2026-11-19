package dev.randomchat.platform.user.controller;

import dev.randomchat.platform.user.dto.UpdateProfileRequest;
import dev.randomchat.platform.user.dto.UserProfileDTO;
import dev.randomchat.platform.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user profile operations.
 * <p>
 * GET  /api/profile/me             – own profile (authenticated)<br>
 * PUT  /api/profile/me             – update own profile (authenticated)<br>
 * GET  /api/profiles/{username}    – public profile lookup (authenticated)
 */
@Tag(name = "Profile", description = "User profile read and update operations")
@RestController
public class ProfileController {

    private final UserProfileService profileService;

    public ProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Get your own profile")
    @GetMapping("/api/profile/me")
    public ResponseEntity<UserProfileDTO> getMyProfile() {
        return ResponseEntity.ok(profileService.getMyProfile());
    }

    @Operation(summary = "Update your own profile")
    @PutMapping("/api/profile/me")
    public ResponseEntity<UserProfileDTO> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateMyProfile(request));
    }

    @Operation(summary = "Get a public profile by username")
    @GetMapping("/api/profiles/{username}")
    public ResponseEntity<UserProfileDTO> getPublicProfile(
            @PathVariable String username) {
        return ResponseEntity.ok(profileService.getPublicProfile(username));
    }
}
