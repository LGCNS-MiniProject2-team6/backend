package com.recu.blog.features.internal.domain.dto;

import java.time.LocalDateTime;

public record CompanyResponseDTO(
        Long companyId,
        String name,
        String summary,
        String mainBusiness,
        String sourceUrl,
        LocalDateTime checkedAt,
        String feedUrl,
        String feedType,
        String logoUrl) {}
