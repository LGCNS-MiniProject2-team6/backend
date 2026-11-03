package com.recu.blog.features.collect.event;

import com.recu.blog.features.collect.domain.CollectPostRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record BlogRequestedEvent(
    @NotNull UUID eventId,
    @NotNull @Pattern(regexp = "BLOG_POST_REQUESTED") String eventType,
    @NotNull @Min(1) @Max(1) Integer eventVersion,
    @NotNull Instant occurredAt,
    @NotNull @Pattern(regexp = "blog-service") String source,
    @NotNull @Valid CollectPostRequest data) {}
