package com.recu.ai.features.internal.user.domain.dto;

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

    private List<Long> haveTechTagIds;

    private List<Long> wantTechTagIds;

    private Integer profileVersion;
}
