package com.recu.user.kafka.event;

import java.util.List;

public record BlogPostCreatedData(
    Long postId,
    Long companyId,
    String title,
    String url,
    List<Long> techTagIds,
    String field,
    String companyName) {}

