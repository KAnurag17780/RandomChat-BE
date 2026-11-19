package dev.randomchat.platform.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code user_blocks} table.
 */
@Entity
@Table(name = "user_blocks")
public class UserBlockEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "blocker_id", nullable = false, updatable = false)
    private UUID blockerId;

    @Column(name = "blocked_user_id", nullable = false, updatable = false)
    private UUID blockedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserBlockEntity() {}

    public UserBlockEntity(UUID id, UUID blockerId, UUID blockedUserId) {
        this.id = id;
        this.blockerId = blockerId;
        this.blockedUserId = blockedUserId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getBlockerId() { return blockerId; }
    public UUID getBlockedUserId() { return blockedUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
