package dev.randomchat.platform.conversation.repository;

import dev.randomchat.platform.conversation.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    /**
     * Find all conversations that the given user participated in, ordered most recent first.
     */
    @Query("""
            SELECT c FROM ConversationEntity c
            JOIN ConversationParticipantEntity cp ON cp.id.conversationId = c.id
            WHERE cp.id.userId = :userId
            ORDER BY c.startedAt DESC
            """)
    List<ConversationEntity> findByParticipantUserId(@Param("userId") UUID userId);

    /**
     * Find a conversation only if the given user is a participant (authorization check).
     */
    @Query("""
            SELECT c FROM ConversationEntity c
            JOIN ConversationParticipantEntity cp ON cp.id.conversationId = c.id
            WHERE c.id = :conversationId AND cp.id.userId = :userId
            """)
    Optional<ConversationEntity> findByIdAndParticipant(@Param("conversationId") UUID conversationId,
                                                         @Param("userId") UUID userId);
}
