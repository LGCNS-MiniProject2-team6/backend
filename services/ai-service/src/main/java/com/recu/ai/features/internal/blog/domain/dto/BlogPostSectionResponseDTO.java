package com.recu.ai.features.internal.blog.domain.dto;

public record BlogPostSectionResponseDTO(
        Long sectionId,
        Integer seq,
        String heading,
        String level,
        String contentText,
        Integer charCount) {}
