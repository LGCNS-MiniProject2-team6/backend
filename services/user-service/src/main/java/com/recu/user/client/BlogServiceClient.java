package com.recu.user.client;

import java.time.LocalDate;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "blog-service")
public interface BlogServiceClient {

  @GetMapping("/api/tech-tags")
  TechResponseDTO getTechTags();

  @GetMapping("/api/posts/{id}")
  PostResponseDTO getPost(
      @PathVariable("id") Long id, @RequestHeader(value = "userId", required = false) Long userId);

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
}
