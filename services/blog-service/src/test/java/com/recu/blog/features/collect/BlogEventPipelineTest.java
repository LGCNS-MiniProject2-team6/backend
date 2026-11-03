package com.recu.blog.features.collect;

import static com.recu.blog.features.collect.config.BlogEventKafkaConfiguration.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.recu.blog.client.*;
import com.recu.blog.commons.exception.*;
import com.recu.blog.features.collect.domain.*;
import com.recu.blog.features.collect.event.*;
import com.recu.blog.features.collect.service.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.active=standalone",
      "spring.cloud.config.enabled=false",
      "spring.cloud.bus.enabled=false",
      "eureka.client.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:blogevents;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "BLOG_COLLECT_API_KEY=test-collect-key",
      "recu.internal.token=test-internal-token"
    })
@EmbeddedKafka(
    partitions = 3,
    topics = {REQUESTED, CREATED, REQUESTED + "-dlt", CREATED + "-dlt"},
    brokerProperties = "auto.create.topics.enable=false",
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@Sql("/blog-fixture.sql")
class BlogEventPipelineTest {
  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired EmbeddedKafkaBroker broker;
  @Autowired KafkaTemplate<String, Object> blogEventKafkaTemplate;
  @MockitoBean UserClient users;
  @MockitoBean AiClient ai;
  @MockitoSpyBean CollectedPostStore store;
  @MockitoSpyBean BlogEventPublisher publisher;
  Consumer<String, String> observer;

  @BeforeEach
  void observe() {
    var config = KafkaTestUtils.consumerProps("assert-" + UUID.randomUUID(), "false", broker);
    observer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer());
    broker.consumeFromEmbeddedTopics(observer, CREATED, REQUESTED + "-dlt");
  }

  @AfterEach
  void close() {
    observer.close();
  }

  CollectPostRequest post() {
    return new CollectPostRequest(
        1L,
        "external-123",
        "Kafka article",
        "https://a.test/" + UUID.randomUUID(),
        OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
        "<h2>Kafka</h2><pre>code</pre>",
        "Kafka content",
        true,
        "Backend",
        "L1",
        "Kafka summary",
        List.of(10L));
  }

  ResponseEntity<JsonNode> collect(Object body, String key, String user) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (key != null) headers.set("X-Collect-Key", key);
    if (user != null) headers.set("userId", user);
    return http.postForEntity(
        "/api/admin/collect/posts", new HttpEntity<>(body, headers), JsonNode.class);
  }

  ConsumerRecord<String, String> awaitMessage(
      String topic, Predicate<ConsumerRecord<String, String>> matches) {
    long end = System.nanoTime() + Duration.ofSeconds(25).toNanos();
    while (System.nanoTime() < end) {
      for (var record : observer.poll(Duration.ofMillis(200))) {
        if (record.topic().equals(topic) && matches.test(record)) return record;
      }
    }
    throw new AssertionError("No matching message in " + topic);
  }

  JsonNode createdFor(String url) throws Exception {
    var record = awaitMessage(CREATED, x -> x.value().contains(url));
    var envelope = json.readTree(record.value());
    assertThat(record.key()).isEqualTo(envelope.path("data").path("postId").asText());
    assertThat(envelope.path("eventType").asText()).isEqualTo("BLOG_POST_CREATED");
    assertThat(envelope.path("occurredAt").asText()).endsWith("Z");
    return envelope;
  }

  long count(String url) {
    return jdbc.queryForObject("select count(*) from blog_post where url=?", Long.class, url);
  }

  @Test
  void authenticatedHttpToKafkaToCommittedPostAndStableCreatedEvent() throws Exception {
    var data = post();
    var first = collect(data, "test-collect-key", null);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(first.getBody().path("eventId").asText()).isNotBlank();
    var one = createdFor(data.url());
    assertThat(count(data.url())).isEqualTo(1);
    assertThat(one.path("data").path("techTagIds").get(0).asLong()).isEqualTo(10);
    assertThat(one.path("data").path("field").asText()).isEqualTo("Backend");
    assertThat(one.path("data").path("companyName").asText()).isEqualTo("Alpha");
    assertThat(
            jdbc.queryForObject(
                "select content_text from blog_post where url=?", String.class, data.url()))
        .isEqualTo("Kafka content");
    assertThat(
            jdbc.queryForObject(
                "select published_at from blog_post where url=?", LocalDateTime.class, data.url()))
        .isEqualTo(LocalDateTime.parse("2026-10-01T03:00:00"));
    assertThat(collect(data, "test-collect-key", null).getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
    var two = createdFor(data.url());
    assertThat(count(data.url())).isEqualTo(1);
    assertThat(two.path("eventId")).isEqualTo(one.path("eventId"));
    assertThat(two.path("occurredAt")).isEqualTo(one.path("occurredAt"));
    verifyNoInteractions(users, ai);
  }

  @Test
  void collectorCredentialCannotBeReplacedByUserIdentityOrInternalToken() {
    assertThat(collect(post(), null, "7").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(collect(post(), "wrong", "7").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(publisher, never()).requested(any());
  }

  @Test
  void invalidHttpInputNeverPublishes() {
    var bad = json.valueToTree(post());
    ((com.fasterxml.jackson.databind.node.ObjectNode) bad).put("url", "ftp://a.test/post");
    assertThat(collect(bad, "test-collect-key", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) bad)
        .put("url", "https://a.test/post")
        .put("companyId", 999);
    assertThat(collect(bad, "test-collect-key", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) bad)
        .put("companyId", 1)
        .putArray("techTagIds")
        .addNull();
    assertThat(collect(bad, "test-collect-key", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(publisher, never()).requested(any());
  }

  @Test
  void failedRequestedPublishReturns503InsteadOfAccepted() {
    doThrow(new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)).when(publisher).requested(any());
    assertThat(collect(post(), "test-collect-key", null).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void createdPublishFailureRetriesWithoutDuplicatingStoredPost() throws Exception {
    doThrow(new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))
        .doCallRealMethod()
        .when(publisher)
        .created(any(), any());
    var data = post();
    assertThat(collect(data, "test-collect-key", null).getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
    createdFor(data.url());
    assertThat(count(data.url())).isEqualTo(1);
    verify(publisher, times(2)).created(any(), any());
    verify(store, times(2)).store(argThat(x -> x.url().equals(data.url())));
  }

  @Test
  void transientFailureRetriesThreeTimesThenGoesToSamePartitionDlt() throws Exception {
    var data = post();
    doThrow(new TransientDataAccessResourceException("temporary test failure"))
        .when(store)
        .store(argThat(x -> x != null && x.url().equals(data.url())));
    collect(data, "test-collect-key", null);
    var record = awaitMessage(REQUESTED + "-dlt", x -> x.key().equals(data.url()));
    assertThat(json.readTree(record.value()).path("data").path("url").asText())
        .isEqualTo(data.url());
    assertThat(record.headers().lastHeader("kafka_dlt-original-partition")).isNotNull();
    verify(store, times(4)).store(argThat(x -> x.url().equals(data.url())));
    assertThat(count(data.url())).isZero();
  }

  @Test
  void invalidEnvelopeGoesStraightToDlt() throws Exception {
    var data = post();
    var event =
        new BlogRequestedEvent(
            UUID.randomUUID(), "WRONG_TYPE", 1, Instant.now(), "blog-service", data);
    blogEventKafkaTemplate.send(REQUESTED, data.url(), event).get();
    awaitMessage(REQUESTED + "-dlt", x -> x.key().equals(data.url()));
    verify(store, never()).store(any());
  }

  @Test
  void malformedJsonPreservesRawBytesInDlt() throws Exception {
    var config = KafkaTestUtils.producerProps(broker);
    try (var raw =
        new KafkaProducer<String, String>(config, new StringSerializer(), new StringSerializer())) {
      String key = "raw-" + UUID.randomUUID();
      raw.send(new ProducerRecord<>(REQUESTED, key, "{broken")).get();
      assertThat(awaitMessage(REQUESTED + "-dlt", x -> key.equals(x.key())).value())
          .isEqualTo("{broken");
    }
    verify(store, never()).store(any());
  }

  @Test
  void concurrentUrlInsertCommitsOnePostAndReturnsSameIdentity() throws Exception {
    var data = post();
    var gate = new java.util.concurrent.CountDownLatch(1);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<CollectedPostStore.StoredPost> work =
          () -> {
            gate.await();
            return store.store(data);
          };
      var first = executor.submit(work);
      var second = executor.submit(work);
      gate.countDown();
      assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).data().postId())
          .isEqualTo(second.get(10, java.util.concurrent.TimeUnit.SECONDS).data().postId());
      assertThat(count(data.url())).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from blog_post_tag t join blog_post p on p.blog_post_id=t.blog_post_id where p.url=?",
                  Long.class,
                  data.url()))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void mismatchedKafkaKeyIsRejectedWithoutStoring() throws Exception {
    var data = post();
    String key = "wrong-" + UUID.randomUUID();
    var event =
        new BlogRequestedEvent(
            UUID.randomUUID(), "BLOG_POST_REQUESTED", 1, Instant.now(), "blog-service", data);
    blogEventKafkaTemplate.send(REQUESTED, key, event).get();
    awaitMessage(REQUESTED + "-dlt", x -> key.equals(x.key()));
    verify(store, never()).store(any());
    assertThat(count(data.url())).isZero();
  }
}
