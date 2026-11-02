package com.recu.blog.internal.dto;

import java.util.List;

public record InternalPostResponse(
    Long postId,
    String title,
    String field,
    String level,
    String contentText,
    List<Section> sections) {
  public record Section(
      Long sectionId, Integer seq, String heading, String contentText, Integer charCount) {}
}
