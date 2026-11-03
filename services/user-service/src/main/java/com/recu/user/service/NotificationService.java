package com.recu.user.service;

import com.recu.user.domain.entity.NotificationEntity;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.domain.entity.UserJobFieldEntity;
import com.recu.user.domain.entity.UserSkillEntity;
import com.recu.user.kafka.event.BlogPostCreatedData;
import com.recu.user.kafka.event.EventEnvelope;
import com.recu.user.repository.NotificationRepository;
import com.recu.user.repository.UserJobFieldRepository;
import com.recu.user.repository.UserRepository;
import com.recu.user.repository.UserSkillRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final UserRepository userRepository;
  private final UserJobFieldRepository userJobFieldRepository;
  private final UserSkillRepository userSkillRepository;
  private final NotificationRepository notificationRepository;

  @Transactional
  public void processBlogPostCreated(EventEnvelope<BlogPostCreatedData> event) {
    String eventId = event.eventId();
    BlogPostCreatedData data = event.data();
    if (data == null || data.postId() == null) {
      throw new IllegalArgumentException(
          "BlogPostCreatedData 또는 postId가 null입니다. eventId: " + eventId);
    }

    Long postId = data.postId();
    String title = data.title() != null ? data.title() : "새 블로그 글";
    String url = data.url() != null ? data.url() : "";
    String field = data.field();
    List<Long> techTagIds = data.techTagIds() != null ? data.techTagIds() : List.of();

    List<UserEntity> allUsers = userRepository.findAll();
    for (UserEntity user : allUsers) {
      if (notificationRepository.existsByUser_UserIdAndEventId(user.getUserId(), eventId)) {
        log.debug("이미 알림이 생성된 사용자입니다. userId={}, eventId={}", user.getUserId(), eventId);
        continue;
      }

      if (isInterestedUser(user, field, techTagIds)) {
        try {
          NotificationEntity notification =
              NotificationEntity.builder()
                  .user(user)
                  .eventId(eventId)
                  .blogPostId(postId)
                  .title(title)
                  .url(url)
                  .isRead(false)
                  .build();
          notificationRepository.save(notification);
          log.info(
              "사용자 알림 생성 완료: userId={}, eventId={}, postId={}", user.getUserId(), eventId, postId);
        } catch (DataIntegrityViolationException e) {
          log.info(
              "중복 알림 방지 처리 (Unique 제약 위반 skip): userId={}, eventId={}", user.getUserId(), eventId);
        }
      }
    }
  }

  private boolean isInterestedUser(UserEntity user, String blogField, List<Long> blogTechTagIds) {
    List<UserJobFieldEntity> userJobFields = userJobFieldRepository.findAllByUser(user);
    List<UserSkillEntity> userSkills = userSkillRepository.findAllByUserAndSkillType(user, "WANT");

    // 프로필 설정이 없는 신규/초기 사용자는 기본적으로 알림 대상에 포함
    if (userJobFields.isEmpty() && userSkills.isEmpty()) {
      return true;
    }

    // 직무 분야 일치 여부 확인
    if (blogField != null && !blogField.isBlank()) {
      boolean fieldMatched =
          userJobFields.stream()
              .anyMatch(
                  jf ->
                      jf.getJobField() != null
                          && jf.getJobField().equalsIgnoreCase(blogField.trim()));
      if (fieldMatched) {
        return true;
      }
    }

    // 관심 기술 태그 일치 여부 확인
    if (!blogTechTagIds.isEmpty()) {
      Set<Long> userSkillIds = new HashSet<>();
      for (UserSkillEntity skill : userSkills) {
        if (skill.getId() != null && skill.getId().getTechTagId() != null) {
          userSkillIds.add(skill.getId().getTechTagId());
        }
      }
      for (Long tagId : blogTechTagIds) {
        if (userSkillIds.contains(tagId)) {
          return true;
        }
      }
    }

    return false;
  }
}
