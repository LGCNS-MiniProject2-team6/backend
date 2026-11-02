package com.recu.blog.internal.dto;

import java.time.LocalDate;
import java.util.List;

public record PostSummaryResponse(List<Post> posts) {
  public record Post(
      Long postId,
      Long companyId,
      String title,
      String companyName,
      String summary,
      List<String> categories,
      List<String> skills,
      LocalDate publishedAt,
      String originalUrl) {}
}
