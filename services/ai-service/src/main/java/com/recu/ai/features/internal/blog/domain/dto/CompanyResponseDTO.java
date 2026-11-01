package com.recu.ai.features.internal.blog.domain.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponseDTO {

    private Long companyId;
    private String name;
    private String summary;
    private String mainBusiness;
    private String sourceUrl;
    private LocalDateTime checkedAt;
    private String feedUrl;
    private String feedType;
    private String logoUrl;
}