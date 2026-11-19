package dev.randomchat.platform.conversation.controller;

import dev.randomchat.platform.auth.service.AuthService;
import dev.randomchat.platform.common.exception.ResourceNotFoundException;
import dev.randomchat.platform.conversation.dto.ConversationDTO;
import dev.randomchat.platform.conversation.dto.MessageDTO;
import dev.randomchat.platform.conversation.entity.ConversationEntity;
import dev.randomchat.platform.conversation.entity.MessageEntity;
import dev.randomchat.platform.conversation.repository.ConversationRepository;
import dev.randomchat.platform.conversation.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for conversation history.
 * <p>
 * Every conversation and message lookup enforces participant authorization:
 * a user may only read conversations they participated in.
 */
@Tag(name = "Conversations", description = "Conversation list and message history")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final AuthService authService;

    public ConversationController(ConversationService conversationService,
                                   ConversationRepository conversationRepository,
                                   AuthService authService) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.authService = authService;
    }

    @Operation(summary = "List all your conversations")
    @GetMapping
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        UUID userId = authService.getAuthenticatedUserId();
        List<ConversationDTO> dtos = conversationService.getUserConversations(userId).stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Get a conversation by ID (must be a participant)")
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDTO> getConversation(
            @PathVariable UUID conversationId) {
        UUID userId = authService.getAuthenticatedUserId();
        ConversationEntity conversation = conversationRepository
                .findByIdAndParticipant(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId.toString()));
        return ResponseEntity.ok(toDTO(conversation));
    }

    @Operation(summary = "Get messages for a conversation (must be a participant)")
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageDTO>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        UUID userId = authService.getAuthenticatedUserId();

        // Authorization: must be a participant
        if (!conversationService.isParticipant(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId.toString());
        }

        List<MessageDTO> messages = conversationService
                .getMessages(conversationId, page, Math.min(limit, 100))
                .stream()
                .map(this::toMessageDTO)
                .toList();

        return ResponseEntity.ok(messages);
    }

    // -----------------------------------------------------------------------
    // Mappers
    // -----------------------------------------------------------------------

    private ConversationDTO toDTO(ConversationEntity c) {
        return new ConversationDTO(c.getId(), c.getStatus(),
                c.getStartedAt(), c.getEndedAt(), c.getEndedReason());
    }

    private MessageDTO toMessageDTO(MessageEntity m) {
        return new MessageDTO(m.getId(), m.getSenderId(),
                m.getContent(), m.getMessageType(), m.getCreatedAt());
    }
}
