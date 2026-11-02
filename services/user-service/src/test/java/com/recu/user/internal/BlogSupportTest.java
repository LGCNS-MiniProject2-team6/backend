package com.recu.user.internal;

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
      "spring.datasource.url=jdbc:h2:mem:useradapter;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
        "CREATE TABLE IF NOT EXISTS app_user(user_id bigint primary key,profile_version int)");
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS user_job_field(id int primary key,user_id bigint,job_field varchar(20))");
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS user_skill(user_id bigint,tech_tag_id bigint,skill_type varchar(10))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS bookmark(user_id bigint,blog_post_id bigint)");
    for (String table : List.of("bookmark", "user_skill", "user_job_field", "app_user")) {
      jdbc.update("delete from " + table);
    }
    jdbc.update(
        "insert into app_user (user_id, google_sub, name, profile_version, role, created_at, updated_at) values (7, 'sub-7', 'tester-7', 2, 'USER', CURRENT_DATE, CURRENT_DATE), (8, 'sub-8', 'tester-8', 1, 'USER', CURRENT_DATE, CURRENT_DATE)");
    jdbc.update("insert into user_job_field (id, user_id, job_field) values (1, 7, 'Backend')");
    jdbc.update(
        "insert into user_skill (tech_tag_id, user_id, skill_type) values (10, 7, 'WANT'), (20, 7, 'HAVE')");
    jdbc.update(
        "insert into bookmark (user_id, blog_post_id, created_at) values (7, 101, CURRENT_TIMESTAMP), (8, 102, CURRENT_TIMESTAMP)");
  }

  @Test
  void profileUsesOnlyUserTablesAndReturnsIdsNotJoinedTechNames() {
    var r = call("/internal/users/recommendation-profile", null, "test-token", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("wantSkillIds").toString()).isEqualTo("[10]");
    assertThat(r.getBody().get("profileVersion").asInt()).isEqualTo(2);
    assertThat(r.getBody().get("jobFields").get(0).asText()).isEqualTo("Backend");
  }

  @Test
  void bookmarkLookupIsScopedToCallerAndRequestedIds() {
    var r = call("/internal/users/bookmarks/lookup", "{\"postIds\":[101,102]}", "test-token", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("postIds").toString()).isEqualTo("[101]");
  }

  @Test
  void missingUserIs404() {
    assertThat(
            call("/internal/users/recommendation-profile", null, "test-token", "999")
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void invalidBatchIs400() {
    assertThat(
            call("/internal/users/bookmarks/lookup", "{\"postIds\":[]}", "test-token", "7")
                .getStatusCode()
                .value())
        .isEqualTo(400);
  }

  @Test
  void internalCredentialAndUserHeaderAreRequired() {
    assertThat(
            call("/internal/users/recommendation-profile", null, null, "7").getStatusCode().value())
        .isEqualTo(401);
    assertThat(
            call("/internal/users/recommendation-profile", null, "test-token", null)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }
}
