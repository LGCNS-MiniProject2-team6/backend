package com.recu.blog.features.post.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

public record PostResponseDTO(
    Long postId,
    String title,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate publishedAt,
    String companyName,
    List<String> categories,
    List<String> skills,
    int charCount,
    String originalUrl,
    String contentHtml,
    List<Section> sections,
    boolean bookmarked) {

  public record Section(int seq, String heading, int charCount) {}
}
