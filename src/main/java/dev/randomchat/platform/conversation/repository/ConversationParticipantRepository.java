package dev.randomchat.platform.conversation.repository;

import dev.randomchat.platform.conversation.entity.ConversationParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipantEntity, ConversationParticipantEntity.ParticipantId> {

    List<ConversationParticipantEntity> findByIdConversationId(UUID conversationId);

    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);
}
