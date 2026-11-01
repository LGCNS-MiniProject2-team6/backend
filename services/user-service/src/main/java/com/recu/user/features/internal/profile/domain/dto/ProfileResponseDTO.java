package com.recu.user.features.internal.profile.domain.dto;

import java.util.List;

public record ProfileResponseDTO(
        String name,
        Long userId,
        List<String> jobFields,
        Integer careerYears,
        List<Long> haveTechTagIds,
        List<Long> wantTechTagIds,
        Integer profileVersion) {
}
