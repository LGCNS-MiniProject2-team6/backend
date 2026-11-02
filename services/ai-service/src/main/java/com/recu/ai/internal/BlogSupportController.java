package com.recu.ai.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/guides")
@ConditionalOnProperty(name = "recu.internal.blog.enabled", havingValue = "true")
public class BlogSupportController {
  private final BlogSupportRepository repository;

  public BlogSupportController(BlogSupportRepository repository) {
    this.repository = repository;
  }

  @PostMapping("/lookup")
  public Map<String, List<Long>> guides(
      @RequestHeader("userId") Long userId, @Valid @RequestBody GuideLookup request) {
    return Map.of(
        "postIds", repository.guides(userId, request.profileVersion(), request.postIds()));
  }

  public record GuideLookup(
      @NotNull @Min(0) Integer profileVersion,
      @NotNull @Size(min = 1, max = 200) List<@NotNull @Positive Long> postIds) {}
}
