package dev.randomchat.platform.moderation.service;

import dev.randomchat.platform.auth.service.AuthService;
import dev.randomchat.platform.common.exception.DuplicateResourceException;
import dev.randomchat.platform.common.exception.ResourceNotFoundException;
import dev.randomchat.platform.conversation.repository.ConversationParticipantRepository;
import dev.randomchat.platform.moderation.dto.BlockedUserDTO;
import dev.randomchat.platform.moderation.dto.ReportRequest;
import dev.randomchat.platform.moderation.entity.UserBlockEntity;
import dev.randomchat.platform.moderation.entity.UserReportEntity;
import dev.randomchat.platform.user.repository.UserBlockRepository;
import dev.randomchat.platform.user.repository.UserRepository;
import dev.randomchat.platform.user.repository.UserReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles block/unblock and report operations.
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final AuthService authService;
    private final UserBlockRepository blockRepository;
    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ConversationParticipantRepository participantRepository;

    public ModerationService(AuthService authService,
                              UserBlockRepository blockRepository,
                              UserReportRepository reportRepository,
                              UserRepository userRepository,
                              ConversationParticipantRepository participantRepository) {
        this.authService = authService;
        this.blockRepository = blockRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public void blockUser(UUID targetUserId) {
        UUID currentUserId = authService.getAuthenticatedUserId();
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot block yourself");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("User", targetUserId.toString());
        }
        if (blockRepository.existsByBlockerIdAndBlockedUserId(currentUserId, targetUserId)) {
            throw new DuplicateResourceException("Block", "target", targetUserId.toString());
        }

        blockRepository.save(new UserBlockEntity(UUID.randomUUID(), currentUserId, targetUserId));
        log.info("User {} blocked user {}", currentUserId, targetUserId);
    }

    @Transactional
    public void unblockUser(UUID targetUserId) {
        UUID currentUserId = authService.getAuthenticatedUserId();
        UserBlockEntity block = blockRepository
                .findByBlockerIdAndBlockedUserId(currentUserId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Block", targetUserId.toString()));
        blockRepository.delete(block);
        log.info("User {} unblocked user {}", currentUserId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserDTO> getBlocks() {
        UUID currentUserId = authService.getAuthenticatedUserId();
        return blockRepository.findByBlockerId(currentUserId).stream()
                .map(b -> new BlockedUserDTO(b.getBlockedUserId(), b.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void submitReport(ReportRequest request) {
        UUID currentUserId = authService.getAuthenticatedUserId();
        UUID reportedUserId = request.reportedUserId();

        if (currentUserId.equals(reportedUserId)) {
            throw new IllegalArgumentException("You cannot report yourself");
        }
        if (!userRepository.existsById(reportedUserId)) {
            throw new ResourceNotFoundException("User", reportedUserId.toString());
        }

        // Verify reporter was a participant in the referenced conversation
        if (request.conversationId() != null) {
            if (!participantRepository.existsByIdConversationIdAndIdUserId(
                    request.conversationId(), currentUserId)) {
                throw new IllegalArgumentException("You were not a participant in that conversation");
            }
        }

        reportRepository.save(new UserReportEntity(
                UUID.randomUUID(), currentUserId, reportedUserId,
                request.conversationId(), request.reason(), request.description()
        ));

        log.info("User {} reported user {} reason={}", currentUserId, reportedUserId, request.reason());
    }
}
