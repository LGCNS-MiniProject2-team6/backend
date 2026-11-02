package com.recu.blog.client;

import com.recu.blog.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "user-service",
    url = "${clients.user.url:}",
    configuration = InternalFeignConfig.class)
public interface UserClient {
  @GetMapping("/internal/users/recommendation-profile")
  RecommendationProfile getRecommendationProfile(@RequestHeader("userId") Long userId);

  @PostMapping("/internal/users/bookmarks/lookup")
  PostIdsResponse findBookmarks(
      @RequestHeader("userId") Long userId, @RequestBody PostIdsRequest request);
}
