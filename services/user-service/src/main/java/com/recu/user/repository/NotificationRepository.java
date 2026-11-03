package com.recu.user.repository;

import com.recu.user.domain.entity.NotificationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

  boolean existsByUser_UserIdAndEventId(Long userId, String eventId);

  List<NotificationEntity> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

  Page<NotificationEntity> findByUser_UserId(Long userId, Pageable pageable);

  long countByUser_UserIdAndIsReadFalse(Long userId);

  Optional<NotificationEntity> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);
}
