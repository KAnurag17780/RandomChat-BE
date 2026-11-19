package dev.randomchat.platform.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code user_reports} table.
 */
@Entity
@Table(name = "user_reports")
public class UserReportEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_id", updatable = false)
    private UUID reporterId;

    @Column(name = "reported_user_id", nullable = false, updatable = false)
    private UUID reportedUserId;

    @Column(name = "conversation_id", updatable = false)
    private UUID conversationId;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserReportEntity() {}

    public UserReportEntity(UUID id, UUID reporterId, UUID reportedUserId,
                             UUID conversationId, String reason, String description) {
        this.id = id;
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.conversationId = conversationId;
        this.reason = reason;
        this.description = description;
        this.status = "PENDING";
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public UUID getReportedUserId() { return reportedUserId; }
    public UUID getConversationId() { return conversationId; }
    public String getReason() { return reason; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
