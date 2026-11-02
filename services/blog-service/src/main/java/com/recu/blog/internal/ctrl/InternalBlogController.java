package com.recu.blog.internal.ctrl;

import com.recu.blog.client.dto.PostIdsRequest;
import com.recu.blog.internal.dto.*;
import com.recu.blog.internal.service.InternalBlogService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalBlogController {
  private final InternalBlogService service;

  @GetMapping("/posts/{postId}")
  public InternalPostResponse content(@PathVariable Long postId) {
    return service.content(postId);
  }

  @GetMapping("/posts/{postId}/exists")
  public Map<String, Boolean> exists(@PathVariable Long postId) {
    return Map.of("exists", service.exists(postId));
  }

  @PostMapping("/posts/lookup")
  public PostSummaryResponse summaries(@Valid @RequestBody PostIdsRequest request) {
    return service.summaries(request.postIds());
  }

  @PostMapping("/tech-tags/lookup")
  public Map<String, Object> tags(@Valid @RequestBody TechIdsRequest request) {
    return service.techTags(request.techTagIds());
  }
}
