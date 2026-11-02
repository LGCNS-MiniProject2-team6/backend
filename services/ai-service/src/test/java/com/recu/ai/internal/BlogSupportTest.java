package com.recu.ai.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.active=standalone",
      "spring.cloud.config.enabled=false",
      "spring.cloud.bus.enabled=false",
      "eureka.client.enabled=false",
      "recu.internal.blog.enabled=true",
      "recu.internal.token=test-token",
      "spring.datasource.url=jdbc:h2:mem:aiadapter;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password="
    })
class BlogSupportTest {
  @Autowired TestRestTemplate http;
  @Autowired DataSource source;
  JdbcTemplate jdbc;

  ResponseEntity<com.fasterxml.jackson.databind.JsonNode> call(
      String path, String json, String token, String user) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.set("X-Internal-Token", token);
    if (user != null) h.set("userId", user);
    return http.exchange(
        path,
        json == null ? HttpMethod.GET : HttpMethod.POST,
        new HttpEntity<>(json, h),
        com.fasterxml.jackson.databind.JsonNode.class);
  }

  @BeforeEach
  void data() {
    jdbc = new JdbcTemplate(source);
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS ai_guide(user_id bigint,blog_post_id bigint,profile_version int)");
    jdbc.update("delete from ai_guide");
    jdbc.update("insert into ai_guide values (7,101,2),(7,102,1),(8,103,2)");
  }

  @Test
  void lookupUsesOnlyAiTableAndMatchesUserAndProfileVersion() {
    var r =
        call(
            "/internal/guides/lookup",
            "{\"profileVersion\":2,\"postIds\":[101,102,103]}",
            "test-token",
            "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("postIds").toString()).isEqualTo("[101]");
  }

  @Test
  void noMatchingGuidesIsEmptyInsteadOfAnError() {
    var r =
        call(
            "/internal/guides/lookup",
            "{\"profileVersion\":3,\"postIds\":[101]}",
            "test-token",
            "7");
    assertThat(r.getBody().get("postIds").size()).isZero();
  }

  @Test
  void missingProfileVersionIs400() {
    assertThat(
            call("/internal/guides/lookup", "{\"postIds\":[101]}", "test-token", "7")
                .getStatusCode()
                .value())
        .isEqualTo(400);
  }

  @Test
  void unknownServiceAndMissingUserAreRejected() {
    assertThat(
            call(
                    "/internal/guides/lookup",
                    "{\"profileVersion\":2,\"postIds\":[101]}",
                    "wrong",
                    "7")
                .getStatusCode()
                .value())
        .isEqualTo(401);
    assertThat(
            call(
                    "/internal/guides/lookup",
                    "{\"profileVersion\":2,\"postIds\":[101]}",
                    "test-token",
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }
}
