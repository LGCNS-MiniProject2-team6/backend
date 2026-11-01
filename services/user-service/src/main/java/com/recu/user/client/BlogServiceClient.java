package com.recu.user.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "blog-service")
public interface BlogServiceClient {

  @GetMapping("/api/tech-tags")
  TechResponseDTO getTechTags();

  record TechResponseDTO(List<TechTagDTO> techTags) {
    public record TechTagDTO(Long techTagId, String name, String field) {}
  }
}

