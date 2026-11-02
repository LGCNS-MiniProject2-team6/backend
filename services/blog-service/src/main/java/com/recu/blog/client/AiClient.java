package com.recu.blog.client;

import com.recu.blog.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "ai-service",
    url = "${clients.ai.url:}",
    configuration = InternalFeignConfig.class)
public interface AiClient {
  @PostMapping("/internal/guides/lookup")
  PostIdsResponse findGuides(
      @RequestHeader("userId") Long userId, @RequestBody GuideLookupRequest request);
}
