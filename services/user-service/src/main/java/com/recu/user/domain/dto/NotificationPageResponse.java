package com.recu.user.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationPageResponse(
    List<Item> notifications,
    int page,
    int size,
    long totalElements,
    int totalPages,
    long unreadCount) {
  public record Item(
      Long id,
      String title,
      String message,
      LocalDateTime createdAt,
      boolean isRead,
      Long postId,
      Long companyId,
      String originalUrl) {}
}
