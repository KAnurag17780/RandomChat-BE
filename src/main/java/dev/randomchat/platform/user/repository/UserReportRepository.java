package dev.randomchat.platform.user.repository;

import dev.randomchat.platform.moderation.entity.UserReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserReportRepository extends JpaRepository<UserReportEntity, UUID> {

    boolean existsByReporterIdAndReportedUserIdAndConversationId(
            UUID reporterId, UUID reportedUserId, UUID conversationId);
}
