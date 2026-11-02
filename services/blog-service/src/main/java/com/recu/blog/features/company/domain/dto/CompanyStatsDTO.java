package com.recu.blog.features.company.domain.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyStatsDTO {

  private Integer postCount;
  private LocalDate firstPublishedAt;
  private LocalDate lastPublishedAt;

  private List<Map<String, Object>> topCategories;
  private List<Map<String, Object>> topSkills;
}
