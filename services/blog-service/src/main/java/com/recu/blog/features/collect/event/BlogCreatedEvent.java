package com.recu.blog.features.collect.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BlogCreatedEvent(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String source,
    Data data) {
  public record Data(
      Long postId, Long companyId, String title, String url, List<Long> techTagIds, String field, String companyName) {}
}
