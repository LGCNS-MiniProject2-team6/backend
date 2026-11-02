package com.recu.ai.features.internal.blog.domain.dto;

import lombok.*;
import java.time.LocalDateTime;

@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostResponseDTO {

    private Long blogPostId;
    private Long companyId;
    private String externalId;
    private String url;
    private String title;
    private LocalDateTime publishedAt;
    private String contentHtml;
    private String contentText;
    private Integer charCount;
    private Boolean hasCode;
    private LocalDateTime collectedAt;
    private Boolean isTech;
    private String field;
    private String level;
    private String summary;

}
