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
public class ProfileRequestDTO {

  private List<String> jobFields;
  private Integer careerYears;
  private List<Long> haveSkillIds;
  private List<Long> wantSkillIds;
}
