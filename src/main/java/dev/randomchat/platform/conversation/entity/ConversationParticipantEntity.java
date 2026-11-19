package dev.randomchat.platform.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code conversation_participants} table.
 */
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipantEntity {

    @EmbeddedId
    private ParticipantId id;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected ConversationParticipantEntity() {}

    public ConversationParticipantEntity(UUID conversationId, UUID userId) {
        this.id = new ParticipantId(conversationId, userId);
    }

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }

    public void markLeft() {
        this.leftAt = Instant.now();
    }

    // Getters
    public ParticipantId getId() { return id; }
    public UUID getConversationId() { return id.conversationId; }
    public UUID getUserId() { return id.userId; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }

    @Embeddable
    public static class ParticipantId implements Serializable {
        @Column(name = "conversation_id")
        private UUID conversationId;

        @Column(name = "user_id")
        private UUID userId;

        protected ParticipantId() {}

        public ParticipantId(UUID conversationId, UUID userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParticipantId that)) return false;
            return Objects.equals(conversationId, that.conversationId)
                    && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}
