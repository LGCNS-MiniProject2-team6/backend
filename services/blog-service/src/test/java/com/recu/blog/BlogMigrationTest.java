package com.recu.blog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.recu.blog.client.*;
import com.recu.blog.client.dto.*;
import com.recu.blog.commons.exception.*;
import com.recu.blog.features.internal.repository.*;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.active=standalone",
      "spring.cloud.config.enabled=false",
      "spring.cloud.bus.enabled=false",
      "eureka.client.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:blogmigration;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "recu.internal.token=test-internal-token"
    })
@Sql("/blog-fixture.sql")
class BlogMigrationTest {
  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired PersonalizationClient personalization;
  @Autowired ApplicationContext context;
  @Autowired EntityManagerFactory entityManagerFactory;
  @MockitoBean UserClient users;
  @MockitoBean AiClient ai;

  @BeforeEach
  void clients() {
    when(users.getRecommendationProfile(7L))
        .thenReturn(new RecommendationProfile(7L, 2, List.of("Backend"), List.of(10L)));
    when(users.findBookmarks(eq(7L), any())).thenReturn(new PostIdsResponse(List.of(101L)));
    when(ai.findGuides(eq(7L), any())).thenReturn(new PostIdsResponse(List.of(101L)));
  }

  ResponseEntity<com.fasterxml.jackson.databind.JsonNode> request(
      HttpMethod method, String path, String body, String user, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (user != null) headers.set("userId", user);
    if (token != null) headers.set("X-Internal-Token", token);
    return http.exchange(
        path,
        method,
        new HttpEntity<>(body, headers),
        com.fasterxml.jackson.databind.JsonNode.class);
  }

  ResponseEntity<com.fasterxml.jackson.databind.JsonNode> get(String path, String user) {
    return request(HttpMethod.GET, path, null, user, null);
  }

  @Test
  void blogDatabaseContainsOnlyItsSixTables() {
    var tables =
        jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema='public'",
            String.class);
    assertThat(tables)
        .containsExactlyInAnyOrder(
            "company",
            "blog_post",
            "blog_post_tag",
            "blog_post_section",
            "blog_post_category",
            "tech_tag");
  }

  @Test
  void companyRecommendationRetainsRankingAndExcludesNonTechPosts() {
    var r = get("/api/companies", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    var companies = r.getBody().get("companies");
    assertThat(companies.get(0).get("companyId").asLong()).isEqualTo(1);
    assertThat(companies.get(0).get("matchedPostCount").asInt()).isEqualTo(2);
    assertThat(companies.get(0).get("totalPostCount").asInt()).isEqualTo(3);
    assertThat(companies.get(0).get("recommended").asBoolean()).isTrue();
    assertThat(companies.get(1).get("recommended").asBoolean()).isFalse();
    verify(users, times(1)).getRecommendationProfile(7L);
    verifyNoInteractions(ai);
  }

  @Test
  void personalizedListRetainsMatchingOrderAndRemoteFlags() {
    var r = get("/api/companies/1/posts", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    var posts = r.getBody().get("posts");
    assertThat(posts.size()).isEqualTo(2);
    assertThat(posts.get(0).get("postId").asLong()).isEqualTo(103);
    assertThat(posts.get(1).get("postId").asLong()).isEqualTo(101);
    assertThat(posts.get(1).get("bookmarked").asBoolean()).isTrue();
    assertThat(posts.get(1).get("hasGuide").asBoolean()).isTrue();
    assertThat(r.getBody().get("filter").get("totalCount").asInt()).isEqualTo(3);
    verify(users, times(1))
        .findBookmarks(eq(7L), argThat(x -> x.postIds().equals(List.of(103L, 102L, 101L))));
    verify(ai, times(1))
        .findGuides(eq(7L), argThat(x -> x.profileVersion() == 2 && x.postIds().size() == 3));
  }

  @Test
  void fullListRetainsDateOrder() {
    var r = get("/api/companies/1/posts?onlyMySkills=false", "7");
    var posts = r.getBody().get("posts");
    assertThat(posts.size()).isEqualTo(3);
    assertThat(posts.get(1).get("postId").asLong()).isEqualTo(102);
  }

  @Test
  void detailRetainsHtmlAnchorsAbsoluteUrlsAndBookmark() {
    var r = get("/api/posts/101", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("contentHtml").asText())
        .contains("id=\"section-1\"", "https://a.test/logo.png");
    assertThat(r.getBody().get("sections").size()).isEqualTo(1);
    assertThat(r.getBody().get("bookmarked").asBoolean()).isTrue();
  }

  @Test
  void missingSectionsUseLegacyFallback() {
    when(users.findBookmarks(eq(7L), any())).thenReturn(new PostIdsResponse(List.of()));
    var r = get("/api/posts/102", "7");
    assertThat(r.getBody().get("sections").size()).isEqualTo(1);
    assertThat(r.getBody().get("contentHtml").asText()).contains("section-1");
  }

  @Test
  void publicReadsDoNotRequireUserOrRemoteServices() {
    assertThat(get("/api/tech-tags", null).getStatusCode().value()).isEqualTo(200);
    var r = get("/api/companies/1", null);
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("stats").get("postCount").asInt()).isEqualTo(3);
    verifyNoInteractions(users, ai);
  }

  @Test
  void missingAndMalformedIdentityAreRejected() {
    assertThat(get("/api/companies", null).getStatusCode().value()).isEqualTo(401);
    assertThat(get("/api/posts/101", "abc").getStatusCode().value()).isEqualTo(401);
    verifyNoInteractions(users, ai);
  }

  @Test
  void missingPostIs404WithoutRemoteCall() {
    var r = get("/api/posts/999", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(404);
    assertThat(r.getBody().get("code").asText()).isEqualTo("NOT_FOUND");
    verifyNoInteractions(users, ai);
  }

  @Test
  void missingProfileKeepsLegacyError() {
    when(users.getRecommendationProfile(7L))
        .thenReturn(new RecommendationProfile(7L, 0, List.of(), List.of()));
    var r = get("/api/companies", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(400);
    assertThat(r.getBody().get("code").asText()).isEqualTo("PROFILE_REQUIRED");
  }

  @Test
  void emptyCompanyDoesNotRequirePersonalization() {
    var r = get("/api/companies/3/posts", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("posts").size()).isZero();
    verifyNoInteractions(users, ai);
  }

  @Test
  void upstreamOutageDoesNotPretendBookmarkIsFalse() {
    when(users.findBookmarks(eq(7L), any()))
        .thenThrow(new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE));
    var r = get("/api/posts/101", "7");
    assertThat(r.getStatusCode().value()).isEqualTo(503);
    assertThat(r.getBody().get("code").asText()).isEqualTo("UPSTREAM_UNAVAILABLE");
  }

  @Test
  void internalContentIsIndependentAndIncludesRawSections() {
    var r = request(HttpMethod.GET, "/internal/posts/101", null, null, "test-internal-token");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("sections").get(0).get("contentText").asText()).isEqualTo("hello");
    assertThat(r.getBody().has("bookmarked")).isFalse();
    verifyNoInteractions(users, ai);
  }

  @Test
  void internalRequestsRequireServiceCredential() {
    assertThat(get("/internal/posts/101", "7").getStatusCode().value()).isEqualTo(401);
    assertThat(
            request(HttpMethod.GET, "/internal/posts/101", null, null, "wrong")
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  void internalBatchLookupsAndValidation() {
    var r =
        request(
            HttpMethod.POST,
            "/internal/posts/lookup",
            "{\"postIds\":[101,999]}",
            null,
            "test-internal-token");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().get("posts").size()).isEqualTo(1);
    r =
        request(
            HttpMethod.POST,
            "/internal/tech-tags/lookup",
            "{\"techTagIds\":[10,999]}",
            null,
            "test-internal-token");
    assertThat(r.getBody().get("missingIds").get(0).asLong()).isEqualTo(999);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/internal/posts/lookup",
                    "{\"postIds\":[]}",
                    null,
                    "test-internal-token")
                .getStatusCode()
                .value())
        .isEqualTo(400);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/internal/posts/lookup",
                    "broken",
                    null,
                    "test-internal-token")
                .getStatusCode()
                .value())
        .isEqualTo(400);
  }

  @Test
  void longListsAreChunkedIntoBatches() {
    when(users.findBookmarks(eq(7L), any())).thenReturn(new PostIdsResponse(List.of()));
    personalization.bookmarks(7L, java.util.stream.LongStream.rangeClosed(1, 401).boxed().toList());
    verify(users, times(3)).findBookmarks(eq(7L), any());
  }

  @Test
  void allBusinessAndInternalReadsUseOneCanonicalModelAndRepositorySet() {
    assertThat(context.getBeansOfType(BlogPostRepository.class))
        .containsOnlyKeys("blogPostRepository");
    assertThat(context.getBeansOfType(BlogPostSectionRepository.class))
        .containsOnlyKeys("blogPostSectionRepository");
    assertThat(context.getBeansOfType(CompanyRepository.class))
        .containsOnlyKeys("companyRepository");
    assertThat(context.getBeansOfType(TechTagRepository.class))
        .containsOnlyKeys("techTagRepository");
    assertThat(entityManagerFactory.getMetamodel().getEntities())
        .hasSize(6)
        .allSatisfy(
            entity ->
                assertThat(entity.getJavaType().getPackageName())
                    .isEqualTo("com.recu.blog.features.internal.domain.entity"));
    assertThat(context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("blogSecurity");
    assertThat(context.containsBean("temporarySecurity")).isFalse();
  }

  @Test
  void teammateAiReadEndpointsKeepTheirOriginalResponseContracts() {
    var post = request(HttpMethod.GET, "/internal/blog/101", null, null, "test-internal-token");
    assertThat(post.getStatusCode().value()).isEqualTo(200);
    assertThat(post.getBody().get("blogPostId").asLong()).isEqualTo(101);
    assertThat(post.getBody().get("companyId").asLong()).isEqualTo(1);
    assertThat(post.getBody().get("contentText").asText()).isEqualTo("spring text");
    var sections =
        request(HttpMethod.GET, "/internal/blog/101/sections", null, null, "test-internal-token");
    assertThat(sections.getBody().get(0).get("seq").asInt()).isEqualTo(1);
    assertThat(sections.getBody().get(0).get("contentText").asText()).isEqualTo("hello");
    var company = request(HttpMethod.GET, "/internal/company/1", null, null, "test-internal-token");
    assertThat(company.getBody().get("name").asText()).isEqualTo("Alpha");
    var tags =
        request(HttpMethod.GET, "/internal/tech-tags?ids=10,20", null, null, "test-internal-token");
    assertThat(tags.getBody().size()).isEqualTo(2);
    assertThat(tags.getBody().get(0).get("techTagId").asLong()).isEqualTo(20);
    verifyNoInteractions(users, ai);
  }

  @Test
  void teammateAiReadEndpointsPreserveNotFoundInsteadOf500() {
    for (String path :
        List.of("/internal/blog/999", "/internal/blog/999/sections", "/internal/company/999")) {
      var result = request(HttpMethod.GET, path, null, null, "test-internal-token");
      assertThat(result.getStatusCode().value()).isEqualTo(404);
      assertThat(result.getBody().get("code").asText()).isEqualTo("NOT_FOUND");
    }
  }

  @Test
  void teammateTechnologyLookupRequiresItsIdsParameter() {
    var result = request(HttpMethod.GET, "/internal/tech-tags", null, null, "test-internal-token");
    assertThat(result.getStatusCode().value()).isEqualTo(400);
    assertThat(result.getBody().get("code").asText()).isEqualTo("INVALID_INPUT");
  }
}
