package com.recu.blog.features.collect.domain;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/** IDs and collection timestamps are assigned by Blog, never by the caller. */
public record CollectPostRequest(
    @NotNull @Positive Long companyId,
    @Size(max = 255) String externalPostId,
    @NotBlank @Size(max = 300) String title,
    @NotBlank @Size(max = 500) @URL(regexp = "https?://.+") String url,
    @NotNull OffsetDateTime publishedAt,
    @Size(max = 500000) String contentHtml,
    @NotBlank @Size(max = 500000) String contentText,
    Boolean isTech,
    @Size(max = 10) String field,
    @Size(max = 4) String level,
    @Size(max = 300) String summary,
    @Size(max = 100) List<@NotNull @Positive Long> techTagIds) {
  public CollectPostRequest {
    techTagIds =
        techTagIds == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(techTagIds));
  }
}
