package com.recu.user.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkResponseDTO {

  private Long bookmarkId;
  private Long postId;
  private Long companyId;
  private String title;
  private String companyName;
  private List<String> categories;
  private String summary;
  private List<String> skills;
  private LocalDate publishedAt;
  private OffsetDateTime savedAt;
  private Boolean hasGuide;
  private LocalDateTime createdAt;
}
