package com.recu.user.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@ConditionalOnProperty(name = "recu.internal.blog.enabled", havingValue = "true")
public class BlogSupportController {
  private final BlogSupportRepository repository;

  public BlogSupportController(BlogSupportRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/recommendation-profile")
  public BlogSupportRepository.RecommendationProfile profile(@RequestHeader("userId") Long userId) {
    return repository.profile(userId);
  }

  @PostMapping("/bookmarks/lookup")
  public Map<String, List<Long>> bookmarks(
      @RequestHeader("userId") Long userId, @Valid @RequestBody PostIds request) {
    return Map.of("postIds", repository.bookmarks(userId, request.postIds()));
  }

  public record PostIds(@NotNull @Size(min = 1, max = 200) List<@NotNull @Positive Long> postIds) {}
}
