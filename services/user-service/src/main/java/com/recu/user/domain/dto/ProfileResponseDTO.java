package com.recu.user.domain.dto;

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
public class ProfileResponseDTO {

  private String name;
  private Long userId;
  private List<String> jobFields;
  private Integer careerYears;
  private List<SkillResponseDTO> haveSkills;
  private List<SkillResponseDTO> wantSkills;
  private int profileVersion;

  public record SkillResponseDTO(Long techTagId, String name) {}
}

