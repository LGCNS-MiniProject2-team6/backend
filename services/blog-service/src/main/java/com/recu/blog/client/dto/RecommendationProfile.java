package com.recu.blog.client.dto;

import java.util.List;

/** User owns these values. Tech names are resolved in Blog's own database. */
public record RecommendationProfile(
    Long userId, Integer profileVersion, List<String> jobFields, List<Long> wantSkillIds) {}
