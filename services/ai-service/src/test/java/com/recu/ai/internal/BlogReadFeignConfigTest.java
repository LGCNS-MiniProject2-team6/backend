package com.recu.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@SpringBootTest(
    properties = {
      "spring.profiles.active=standalone", "spring.cloud.config.enabled=false",
      "spring.cloud.bus.enabled=false", "eureka.client.enabled=false",
      "recu.internal.token=feign-contract-test-token"
    })
@Import(BlogReadFeignConfigTest.Clients.class)
class BlogReadFeignConfigTest {
  static final Map<String, String> received = new ConcurrentHashMap<>();
  static final HttpServer server;

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            String path = exchange.getRequestURI().getPath();
            received.put(
                path, String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Token")));
            String body =
                path.endsWith("/sections") || path.equals("/internal/tech-tags")
                    ? "[]"
                    : "{\"blogPostId\":101}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void endpoint(DynamicPropertyRegistry registry) {
    registry.add("test.blog.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterAll
  static void closeServer() {
    server.stop(0);
  }

  @Autowired BlogContractClient blog;
  @Autowired OtherServiceClient other;

  @Test
  void sendsAuthenticationForTheTeammateBlogReadContracts() {
    assertThat(blog.post(101L).get("blogPostId")).isEqualTo(101);
    blog.sections(101L);
    blog.company(1L);
    blog.techTags(List.of(10L, 20L));
    for (String path :
        List.of(
            "/internal/blog/101",
            "/internal/blog/101/sections",
            "/internal/company/1",
            "/internal/tech-tags")) {
      assertThat(received.get(path)).isEqualTo("feign-contract-test-token");
    }
  }

  @Test
  void doesNotLeakTheBlogCredentialToOtherFeignTargets() {
    other.call();
    assertThat(received.get("/other")).isEqualTo("null");
  }

  @TestConfiguration
  @EnableFeignClients(clients = {BlogContractClient.class, OtherServiceClient.class})
  static class Clients {}

  @FeignClient(name = "blog-service", url = "${test.blog.url}")
  interface BlogContractClient {
    @GetMapping("/internal/blog/{postId}")
    Map<String, Object> post(@PathVariable("postId") Long postId);

    @GetMapping("/internal/blog/{postId}/sections")
    List<Map<String, Object>> sections(@PathVariable("postId") Long postId);

    @GetMapping("/internal/company/{companyId}")
    Map<String, Object> company(@PathVariable("companyId") Long companyId);

    @GetMapping("/internal/tech-tags")
    List<Map<String, Object>> techTags(@RequestParam("ids") List<Long> ids);
  }

  @FeignClient(name = "other-service", url = "${test.blog.url}")
  interface OtherServiceClient {
    @GetMapping("/other")
    Map<String, Object> call();
  }
}
