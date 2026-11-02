package com.recu.blog.features.company.domain.dto;

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
public class CompanyListResponseDTO {

  private List<CompanyDTO> companies;

  public record CompanyDTO(
      Long companyId,
      String name,
      String summary,
      String logoUrl,
      Integer matchedSkillCount,
      Integer totalSkillCount,
      Integer matchedPostCount,
      Integer totalPostCount,
      List<SkillResponseDTO> matchedSkills,
      boolean recommended) {}

  public record SkillResponseDTO(String name, Integer postCount) {}
}
