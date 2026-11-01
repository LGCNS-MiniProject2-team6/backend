package com.recu.blog.features.company.domain.dto;

import java.time.LocalDate;
import java.util.List;

public record CompanyPostListResponseDTO(Filter filter, List<Post> posts) {

  public record Filter(boolean onlyMySkills, int matchedCount, int totalCount) {}

  public record Post(
      Long postId,
      String title,
      LocalDate publishedAt,
      List<String> categories,
      String level,
      String summary,
      List<String> skills,
      List<String> matchedSkills,
      Integer charCount,
      int sectionCount,
      String originalUrl,
      boolean bookmarked,
      boolean hasGuide) {}
}
