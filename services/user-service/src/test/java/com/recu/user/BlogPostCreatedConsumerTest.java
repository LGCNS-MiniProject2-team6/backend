package com.recu.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.user.domain.entity.NotificationEntity;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.domain.entity.UserJobFieldEntity;
import com.recu.user.domain.entity.UserSkillEntity;
import com.recu.user.domain.entity.UserSkillPk;
import com.recu.user.kafka.consumer.BlogPostCreatedConsumer;
import com.recu.user.kafka.event.BlogPostCreatedData;
import com.recu.user.kafka.event.EventEnvelope;
import com.recu.user.repository.NotificationRepository;
import com.recu.user.repository.UserJobFieldRepository;
import com.recu.user.repository.UserRepository;
import com.recu.user.repository.UserSkillRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("standalone")
@Transactional
class BlogPostCreatedConsumerTest {

  @Autowired private BlogPostCreatedConsumer consumer;
  @Autowired private UserRepository userRepository;
  @Autowired private UserJobFieldRepository userJobFieldRepository;
  @Autowired private UserSkillRepository userSkillRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ObjectMapper objectMapper;

  private UserEntity user;

  @BeforeEach
  void setUp() {
    user =
        UserEntity.builder()
            .googleSub("test-sub-123")
            .name("테스터")
            .careerYears(2)
            .profileVersion(1)
            .role("USER")
            .createdAt(LocalDate.now())
            .updatedAt(LocalDate.now())
            .build();
    userRepository.save(user);

    userJobFieldRepository.save(
        UserJobFieldEntity.builder().user(user).jobField("백엔드").build());

    userSkillRepository.save(
        UserSkillEntity.builder()
            .id(new UserSkillPk(user.getUserId(), 100L))
            .user(user)
            .skillType("WANT")
            .build());
  }

  @Test
  @DisplayName("블로그 생성 이벤트 수신 시 관심 사용자에 대해 알림이 저장된다")
  void consume_CreatesNotificationForInterestedUser() throws Exception {
    BlogPostCreatedData data =
        new BlogPostCreatedData(
            1L, 10L, "스프링 부트와 카프카 연동", "https://example.com/post/1", List.of(100L), "백엔드", "테크기업");

    EventEnvelope<BlogPostCreatedData> envelope =
        new EventEnvelope<>(
            "event-uuid-001",
            "BLOG_POST_CREATED",
            1,
            Instant.now(),
            "blog-service",
            data);

    String json = objectMapper.writeValueAsString(envelope);
    ConsumerRecord<String, Object> record =
        new ConsumerRecord<>("blog-post.created.v1", 0, 0L, "1", json);

    consumer.consume(record);

    List<NotificationEntity> notifications =
        notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
    assertThat(notifications).hasSize(1);
    NotificationEntity noti = notifications.get(0);
    assertThat(noti.getEventId()).isEqualTo("event-uuid-001");
    assertThat(noti.getBlogPostId()).isEqualTo(1L);
    assertThat(noti.getTitle()).isEqualTo("스프링 부트와 카프카 연동");
    assertThat(noti.getUrl()).isEqualTo("https://example.com/post/1");
  }

  @Test
  @DisplayName("동일 이벤트가 중복 수신될 경우 중복 알림이 발생하지 않고 건너뛴다")
  void consume_DuplicateEvent_IsSkipped() throws Exception {
    BlogPostCreatedData data =
        new BlogPostCreatedData(
            2L, 10L, "카프카 심화", "https://example.com/post/2", List.of(100L), "백엔드", "테크기업");

    EventEnvelope<BlogPostCreatedData> envelope =
        new EventEnvelope<>(
            "event-uuid-002",
            "BLOG_POST_CREATED",
            1,
            Instant.now(),
            "blog-service",
            data);

    String json = objectMapper.writeValueAsString(envelope);
    ConsumerRecord<String, Object> record1 =
        new ConsumerRecord<>("blog-post.created.v1", 0, 0L, "2", json);
    ConsumerRecord<String, Object> record2 =
        new ConsumerRecord<>("blog-post.created.v1", 0, 1L, "2", json);

    consumer.consume(record1);
    consumer.consume(record2);

    List<NotificationEntity> notifications =
        notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
    assertThat(notifications).hasSize(1);
  }
}

