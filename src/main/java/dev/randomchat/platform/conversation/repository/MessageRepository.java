package dev.randomchat.platform.conversation.repository;

import dev.randomchat.platform.conversation.entity.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    /**
     * Cursor-based pagination: messages in a conversation before or at a given timestamp+id.
     */
    Page<MessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId, Pageable pageable);

    /**
     * Idempotency check: find message by sender + client-generated ID.
     */
    Optional<MessageEntity> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    /**
     * Count messages in conversation (for metrics).
     */
    long countByConversationId(UUID conversationId);
}
