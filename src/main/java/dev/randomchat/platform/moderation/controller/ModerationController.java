package dev.randomchat.platform.moderation.controller;

import dev.randomchat.platform.moderation.dto.BlockedUserDTO;
import dev.randomchat.platform.moderation.dto.ReportRequest;
import dev.randomchat.platform.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for moderation operations: block/unblock users and submit reports.
 */
@Tag(name = "Moderation", description = "Block/unblock users and submit abuse reports")
@RestController
@RequestMapping("/api")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Operation(summary = "Block a user")
    @PostMapping("/users/{userId}/block")
    public ResponseEntity<Map<String, String>> blockUser(@PathVariable UUID userId) {
        moderationService.blockUser(userId);
        return ResponseEntity.ok(Map.of("message", "User blocked"));
    }

    @Operation(summary = "Unblock a user")
    @DeleteMapping("/users/{userId}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable UUID userId) {
        moderationService.unblockUser(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get your block list")
    @GetMapping("/blocks")
    public ResponseEntity<List<BlockedUserDTO>> getBlocks() {
        return ResponseEntity.ok(moderationService.getBlocks());
    }

    @Operation(summary = "Submit an abuse report")
    @PostMapping("/reports")
    public ResponseEntity<Map<String, String>> submitReport(
            @Valid @RequestBody ReportRequest request) {
        moderationService.submitReport(request);
        return ResponseEntity.ok(Map.of("message", "Report submitted"));
    }
}
