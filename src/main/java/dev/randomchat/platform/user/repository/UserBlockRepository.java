package dev.randomchat.platform.user.repository;

import dev.randomchat.platform.moderation.entity.UserBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlockEntity, UUID> {

    boolean existsByBlockerIdAndBlockedUserId(UUID blockerId, UUID blockedUserId);

    Optional<UserBlockEntity> findByBlockerIdAndBlockedUserId(UUID blockerId, UUID blockedUserId);

    List<UserBlockEntity> findByBlockerId(UUID blockerId);
}
