package com.recu.user.kafka.event;

import java.time.Instant;

public record EventEnvelope<T>(
    String eventId,
    String eventType,
    Integer eventVersion,
    Instant occurredAt,
    String source,
    T data) {}

