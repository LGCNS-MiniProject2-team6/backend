package com.recu.blog.features.company.domain.dto;

import java.time.LocalDate;
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
public class CompanyResponseDTO {
  private Long companyId;
  private String name;
  private String logoUrl;
  private String summary;
  private String mainBusiness;
  private String sourceUrl;
  private LocalDate checkedAt;

  private CompanyStatsDTO stats;
}
