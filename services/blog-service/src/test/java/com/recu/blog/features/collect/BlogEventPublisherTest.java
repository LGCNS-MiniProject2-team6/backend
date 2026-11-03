package com.recu.blog.features.collect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.recu.blog.commons.exception.BusinessException;
import com.recu.blog.features.collect.domain.CollectPostRequest;
import com.recu.blog.features.collect.event.BlogRequestedEvent;
import com.recu.blog.features.collect.service.BlogEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class BlogEventPublisherTest {
  @Test
  void waitsForBrokerAcknowledgementAndPropagatesAsynchronousFailure() throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    var ack = new CompletableFuture<SendResult<String, Object>>();
    when(template.send(anyString(), anyString(), any())).thenReturn(ack);
    var publisher = new BlogEventPublisher(template);
    var data =
        new CollectPostRequest(
            1L,
            null,
            "Title",
            "https://a.test/ack",
            OffsetDateTime.parse("2026-10-01T00:00:00Z"),
            null,
            "Text",
            null,
            null,
            null,
            null,
            List.of());
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> publisher.requested(data));
      verify(template, timeout(3000))
          .send(eq("blog-post.requested.v1"), eq(data.url()), any(BlogRequestedEvent.class));
      assertThat(result.isDone()).isFalse();
      ack.completeExceptionally(new IllegalStateException("broker unavailable"));
      assertThatThrownBy(() -> result.get(3, TimeUnit.SECONDS))
          .hasCauseInstanceOf(BusinessException.class);
    } finally {
      executor.shutdownNow();
    }
  }
}
