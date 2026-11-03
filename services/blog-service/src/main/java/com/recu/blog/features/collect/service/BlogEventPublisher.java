package com.recu.blog.features.collect.service;

import static com.recu.blog.features.collect.config.BlogEventKafkaConfiguration.*;

import com.recu.blog.commons.exception.*;
import com.recu.blog.features.collect.domain.CollectPostRequest;
import com.recu.blog.features.collect.event.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class BlogEventPublisher {
  private final KafkaTemplate<String, Object> template;

  public BlogEventPublisher(
      @Qualifier("blogEventKafkaTemplate") KafkaTemplate<String, Object> template) {
    this.template = template;
  }

  public UUID requested(CollectPostRequest data) {
    UUID id = UUID.randomUUID();
    send(
        REQUESTED,
        data.url(),
        new BlogRequestedEvent(id, "BLOG_POST_REQUESTED", 1, Instant.now(), "blog-service", data));
    return id;
  }

  public void created(BlogCreatedEvent.Data data, Instant collectedAt) {
    // URL duplicates and DLT replay must use the same ID, including after a process restart.
    UUID id =
        UUID.nameUUIDFromBytes(
            ("blog-post.created.v1:" + data.postId()).getBytes(StandardCharsets.UTF_8));
    send(
        CREATED,
        data.postId().toString(),
        new BlogCreatedEvent(id, "BLOG_POST_CREATED", 1, collectedAt, "blog-service", data));
  }

  private void send(String topic, String key, Object event) {
    try {
      template.send(topic, key, event).get(15, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    }
  }
}
