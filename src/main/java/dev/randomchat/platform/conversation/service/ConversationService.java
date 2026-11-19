package dev.randomchat.platform.conversation.service;

import dev.randomchat.platform.conversation.entity.ConversationEntity;
import dev.randomchat.platform.conversation.entity.ConversationParticipantEntity;
import dev.randomchat.platform.conversation.entity.MessageEntity;
import dev.randomchat.platform.conversation.repository.ConversationParticipantRepository;
import dev.randomchat.platform.conversation.repository.ConversationRepository;
import dev.randomchat.platform.conversation.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles persistent conversation and message operations.
 * <p>
 * All methods here run in transactions separate from the matchmaking lock.
 * Never call these methods while holding the matchmaking lock.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                                ConversationParticipantRepository participantRepository,
                                MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Creates a conversation record and two participant records atomically.
     */
    @Transactional
    public ConversationEntity createConversation(UUID conversationId, UUID userIdA, UUID userIdB) {
        ConversationEntity conversation = new ConversationEntity(conversationId);
        conversationRepository.save(conversation);

        participantRepository.save(new ConversationParticipantEntity(conversationId, userIdA));
        participantRepository.save(new ConversationParticipantEntity(conversationId, userIdB));

        log.info("Created conversation {} between {} and {}", conversationId, userIdA, userIdB);
        return conversation;
    }

    /**
     * Marks a conversation as ended with the given reason.
     */
    @Transactional
    public void endConversation(UUID conversationId, String reason) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.end(reason);
            conversationRepository.save(c);
            log.info("Ended conversation {} with reason {}", conversationId, reason);
        });
    }

    /**
     * Persists a message. If {@code clientMessageId} is provided and a message with the
     * same sender+clientId already exists, returns the existing message (idempotent).
     *
     * @throws IllegalArgumentException if the content is blank or too long
     */
    @Transactional
    public MessageEntity persistMessage(UUID conversationId, UUID senderId,
                                         UUID clientMessageId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
        if (content.length() > 2000) {
            throw new IllegalArgumentException("Message content exceeds maximum length");
        }

        // Idempotency: if client ID already seen, return existing message
        if (clientMessageId != null) {
            Optional<MessageEntity> existing = messageRepository
                    .findBySenderIdAndClientMessageId(senderId, clientMessageId);
            if (existing.isPresent()) {
                log.debug("Duplicate message detected, returning existing: clientId={}", clientMessageId);
                return existing.get();
            }
        }

        MessageEntity message = new MessageEntity(
                UUID.randomUUID(), conversationId, senderId, clientMessageId, content);
        try {
            return messageRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            // Race condition on unique constraint – fetch the existing record
            if (clientMessageId != null) {
                return messageRepository
                        .findBySenderIdAndClientMessageId(senderId, clientMessageId)
                        .orElseThrow(() -> new RuntimeException("Idempotency failure", e));
            }
            throw e;
        }
    }

    /**
     * Returns all conversations for a user (participant-authorized).
     */
    @Transactional(readOnly = true)
    public List<ConversationEntity> getUserConversations(UUID userId) {
        return conversationRepository.findByParticipantUserId(userId);
    }

    /**
     * Paged message history for a conversation (participant-authorized by caller).
     */
    @Transactional(readOnly = true)
    public List<MessageEntity> getMessages(UUID conversationId, int page, int size) {
        return messageRepository
                .findByConversationIdOrderByCreatedAtAscIdAsc(conversationId,
                        PageRequest.of(page, Math.min(size, 100)))
                .getContent();
    }

    /**
     * Checks if a user is a participant in the given conversation.
     */
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participantRepository.existsByIdConversationIdAndIdUserId(conversationId, userId);
    }
}
