package com.recu.user.client;

import java.time.LocalDate;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "blog-service", configuration = BlogServiceClientConfig.class)
public interface BlogServiceClient {

  @GetMapping("/api/tech-tags")
  TechResponseDTO getTechTags();

  @GetMapping("/api/posts/{id}")
  PostResponseDTO getPost(
      @PathVariable("id") Long id, @RequestHeader(value = "userId", required = false) Long userId);

  @PostMapping("/internal/posts/lookup")
  PostSummaryResponse lookupPosts(@RequestBody PostIdsRequest request);

  @GetMapping("/internal/posts/{id}/exists")
  ExistsResponse existsPost(@PathVariable("id") Long id);

  record TechResponseDTO(List<TechTagDTO> techTags) {
    public record TechTagDTO(Long techTagId, String name, String field) {}
  }

  record PostResponseDTO(
      Long postId,
      Long companyId,
      String title,
      LocalDate publishedAt,
      String companyName,
      List<String> categories,
      String summary,
      List<String> skills,
      String originalUrl) {}

  record PostIdsRequest(List<Long> postIds) {}

  record PostSummaryResponse(List<PostSummaryDTO> posts) {}

  record PostSummaryDTO(
      Long postId,
      Long companyId,
      String title,
      String companyName,
      String summary,
      List<String> categories,
      List<String> skills,
      LocalDate publishedAt,
      String originalUrl) {}

  record ExistsResponse(boolean exists) {}
}
