package com.recu.user.repository;

import com.recu.user.domain.entity.NotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

  boolean existsByUser_UserIdAndEventId(Long userId, String eventId);

  List<NotificationEntity> findByUser_UserIdOrderByCreatedAtDesc(Long userId);
}
