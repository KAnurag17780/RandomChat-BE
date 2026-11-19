package dev.randomchat.platform.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code conversations} table.
 * Represents a persistent chat conversation record.
 */
@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_reason", length = 30)
    private String endedReason;

    protected ConversationEntity() {}

    public ConversationEntity(UUID id) {
        this.id = id;
        this.status = "ACTIVE";
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public void end(String reason) {
        this.status = "ENDED";
        this.endedAt = Instant.now();
        this.endedReason = reason;
    }

    // Getters
    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getEndedReason() { return endedReason; }
}
