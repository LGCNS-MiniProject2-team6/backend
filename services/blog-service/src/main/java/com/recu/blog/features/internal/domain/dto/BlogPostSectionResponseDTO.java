package com.recu.blog.features.internal.domain.dto;

public record BlogPostSectionResponseDTO(
    Long sectionId,
    Integer seq,
    String heading,
    String level,
    String contentText,
    Integer charCount) {}
