package com.recu.blog.features.internal.domain.dto;

import java.time.LocalDateTime;

public record PostResponseDTO(
    Long blogPostId,
    Long companyId,
    String externalId,
    String url,
    String title,
    LocalDateTime publishedAt,
    String contentHtml,
    String contentText,
    Integer charCount,
    Boolean hasCode,
    LocalDateTime collectedAt,
    Boolean isTech,
    String field,
    String level,
    String summary) {}
