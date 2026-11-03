package com.recu.user.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.user.kafka.event.BlogPostCreatedData;
import com.recu.user.kafka.event.EventEnvelope;
import com.recu.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogPostCreatedConsumer {

  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  @KafkaListener(
      topics = "blog-post.created.v1",
      groupId = "user-service",
      properties = "auto.offset.reset=earliest")
  public void consume(ConsumerRecord<String, Object> record) {
    String key = record.key();
    Object value = record.value();
    log.info(
        "Received blog-post.created.v1 event: partition={}, offset={}, key={}",
        record.partition(),
        record.offset(),
        key);

    try {
      EventEnvelope<BlogPostCreatedData> envelope;
      if (value instanceof String strVal) {
        envelope =
            objectMapper.readValue(
                strVal, new TypeReference<EventEnvelope<BlogPostCreatedData>>() {});
      } else {
        envelope =
            objectMapper.convertValue(
                value, new TypeReference<EventEnvelope<BlogPostCreatedData>>() {});
      }

      if (envelope == null || envelope.eventId() == null) {
        throw new IllegalArgumentException("유효하지 않은 이벤트 포맷입니다 (eventId 누락)");
      }
      if (envelope.data() == null || envelope.data().postId() == null) {
        throw new IllegalArgumentException("유효하지 않은 이벤트 포맷입니다 (data 또는 postId 누락)");
      }

      notificationService.processBlogPostCreated(envelope);
    } catch (IllegalArgumentException e) {
      log.error("검증 실패로 인한 DLT 즉시 처리 대상 예외 발생: key={}", key, e);
      throw e;
    } catch (Exception e) {
      log.error("블로그 생성 이벤트 처리 중 오류 발생 (재시도 대상): key={}", key, e);
      throw new RuntimeException("이벤트 처리 실패: key=" + key, e);
    }
  }
}
