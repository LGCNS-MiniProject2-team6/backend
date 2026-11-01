package com.recu.ai.features.domain.dto;

import java.util.List;

public record GuideResponseDTO(
        Long guideId,
        boolean cached,
        Basis basis,
        List<Integer> focusSections,
        List<SectionBadge> sectionBadges,
        String section1Text,
        String section2Text,
        String section3Text) {

    public record Basis(Integer careerYears, List<String> haveSkills, List<String> wantSkills) {}

    public record SectionBadge(int seq, String badge) {}
}
