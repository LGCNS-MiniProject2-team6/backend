package com.recu.ai.features.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.ai.commons.exception.BusinessException;
import com.recu.ai.commons.exception.ErrorCode;
import com.recu.ai.features.domain.dto.GuideResponseDTO;
import com.recu.ai.features.domain.dto.GuideResponseDTO.Basis;
import com.recu.ai.features.domain.dto.GuideResponseDTO.SectionBadge;
import com.recu.ai.features.domain.entity.AiGuideEntity;
import com.recu.ai.features.internal.blog.domain.dto.BlogPostSectionResponseDTO;
import com.recu.ai.features.internal.blog.domain.dto.PostResponseDTO;
import com.recu.ai.features.internal.blog.domain.dto.TechTagResponseDTO;
import com.recu.ai.features.internal.blog.feign.BlogClient;
import com.recu.ai.features.internal.user.domain.dto.ProfileResponseDTO;
import com.recu.ai.features.internal.user.feign.UserClient;
import com.recu.ai.features.repository.GuideRepository;
import feign.FeignException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class GuideService {

    private static final Set<String> OUTPUT_KEYS =
            Set.of("focusSections", "sectionBadges", "section1Text", "section2Text", "section3Text");
    private static final Set<String> BADGE_KEYS = Set.of("seq", "badge");
    private static final Set<String> BADGES = Set.of("KEY", "LIGHT", "SKIP", "REF");

    private final GuideRepository guideRepository;
    private final GeminiGuideClient geminiClient;
    private final ObjectMapper objectMapper;
    private final String promptVersion;
    private final UserClient userClient;
    private final BlogClient blogClient;

    public GuideService(
            GuideRepository guideRepository,
            GeminiGuideClient geminiClient,
            ObjectMapper objectMapper,
            UserClient userClient,
            BlogClient blogClient,
            @Value("${gemini.prompt-version}") String promptVersion) {
        this.guideRepository = guideRepository;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.promptVersion = promptVersion;
        this.userClient = userClient;
        this.blogClient = blogClient;
    }

    public GuideResponseDTO generateGuide(Long userId, Long postId) {

        ProfileResponseDTO userProfile;
        GuideProfile profile;
        PostResponseDTO post;
        try {
            userProfile = userClient.getProfile(userId);
            if (userProfile.getCareerYears() == null) {
                throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
            }
            post = blogClient.getPost(postId);
            profile = toGuideProfile(userProfile);
        } catch (FeignException.NotFound exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        } catch (FeignException exception) {
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }

        return guideRepository
                .findCached(userId, post.getBlogPostId(), profile.profileVersion(), promptVersion)
                .map(guide -> response(guide, profile, true))
                .orElseGet(() -> generate(profile, post));
    }

    private GuideProfile toGuideProfile(ProfileResponseDTO profile) {
        List<Long> haveIds = profile.getHaveTechTagIds() == null ? List.of() : profile.getHaveTechTagIds();
        List<Long> wantIds = profile.getWantTechTagIds() == null ? List.of() : profile.getWantTechTagIds();
        Set<Long> requestedIds = new LinkedHashSet<>();
        requestedIds.addAll(haveIds);
        requestedIds.addAll(wantIds);

        List<TechTagResponseDTO> tags =
                requestedIds.isEmpty() ? List.of() : blogClient.getTechTags(List.copyOf(requestedIds));
        Map<Long, String> namesById = new HashMap<>();
        tags.forEach(tag -> namesById.put(tag.techTagId(), tag.name()));
        if (namesById.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }

        return new GuideProfile(
                profile.getUserId(),
                profile.getCareerYears(),
                profile.getJobFields() == null ? List.of() : profile.getJobFields(),
                resolveNames(haveIds, namesById),
                resolveNames(wantIds, namesById),
                profile.getProfileVersion());
    }

    private List<String> resolveNames(List<Long> tagIds, Map<Long, String> namesById) {
        return tagIds.stream().map(namesById::get).toList();
    }

    private GuideResponseDTO generate(GuideProfile profile, PostResponseDTO post) {
        List<BlogPostSectionResponseDTO> sections;
        try {
            sections = blogClient.getSections(post.getBlogPostId());
        } catch (FeignException exception) {
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
        GeneratedGuide generated =
                parseAndValidate(callGemini(buildPrompt(profile, post, sections)), sections.size());

        AiGuideEntity guide =
                AiGuideEntity.builder()
                        .user_id(profile.userId())
                        .blog_post_id(post.getBlogPostId())
                        .profile_version(profile.profileVersion())
                        .prompt_version(promptVersion)
                        .focus_sections(writeJson(generated.focusSections()))
                        .section_badges(writeJson(generated.sectionBadges()))
                        .section1_text(generated.section1Text())
                        .section2_text(generated.section2Text())
                        .section3_text(generated.section3Text())
                        .created_at(LocalDateTime.now())
                        .build();

        try {
            return response(guideRepository.save(guide), profile, false);
        } catch (DataIntegrityViolationException exception) {
            return guideRepository
                    .findCached(
                            profile.userId(),
                            post.getBlogPostId(),
                            profile.profileVersion(),
                            promptVersion)
                    .map(saved -> response(saved, profile, true))
                    .orElseThrow(this::aiUnavailable);
        }
    }

    private String callGemini(String prompt) {
        try {
            return geminiClient.generate(prompt);
        } catch (Exception exception) {
            throw aiUnavailable();
        }
    }

    private GeneratedGuide parseAndValidate(String raw, int sectionCount) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isObject() || !fieldNames(root).equals(OUTPUT_KEYS)) {
                throw aiUnavailable();
            }

            JsonNode badges = root.get("sectionBadges");
            if (!badges.isArray()) {
                throw aiUnavailable();
            }
            badges.forEach(
                    badge -> {
                        if (!badge.isObject() || !fieldNames(badge).equals(BADGE_KEYS)) {
                            throw aiUnavailable();
                        }
                    });

            GeneratedGuide generated = objectMapper.treeToValue(root, GeneratedGuide.class);
            validate(generated, sectionCount);
            return generated;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw aiUnavailable();
        }
    }

    private void validate(GeneratedGuide generated, int sectionCount) {
        if (generated.focusSections() == null
                || generated.sectionBadges() == null
                || generated.sectionBadges().size() != sectionCount
                || generated.section1Text() == null
                || generated.section2Text() == null
                || generated.section3Text() == null) {
            throw aiUnavailable();
        }

        List<Integer> keySections = new ArrayList<>();
        for (int index = 0; index < generated.sectionBadges().size(); index++) {
            SectionBadge sectionBadge = generated.sectionBadges().get(index);
            if (sectionBadge == null
                    || sectionBadge.seq() != index + 1
                    || !BADGES.contains(sectionBadge.badge())) {
                throw aiUnavailable();
            }
            if ("KEY".equals(sectionBadge.badge())) {
                keySections.add(sectionBadge.seq());
            }
        }

        if (sectionCount > 1 && keySections.size() == sectionCount) {
            throw aiUnavailable();
        }

        if (generated.focusSections().stream()
                .anyMatch(seq -> seq == null || seq < 1 || seq > sectionCount)
                || new LinkedHashSet<>(generated.focusSections()).size() != generated.focusSections().size()
                || !generated.focusSections().equals(keySections)) {
            throw aiUnavailable();
        }
    }

    private GuideResponseDTO response(
            AiGuideEntity guide, GuideProfile profile, boolean cached) {
        return new GuideResponseDTO(
                guide.getAi_guide_id(),
                cached,
                basis(profile),
                readJson(guide.getFocus_sections(), new TypeReference<List<Integer>>() {}),
                readJson(guide.getSection_badges(), new TypeReference<List<SectionBadge>>() {}),
                guide.getSection1_text(),
                guide.getSection2_text(),
                guide.getSection3_text());

    }

    private Basis basis(GuideProfile profile) {
        return new Basis(profile.careerYears(), profile.haveSkills(), profile.wantSkills());
    }

    private String buildPrompt(
            GuideProfile profile,
            PostResponseDTO post,
            List<BlogPostSectionResponseDTO> sections) {
        StringBuilder input =
                new StringBuilder(loadPrompt())
                        .append("\n\n[프로필]\n경력 연차: ")
                        .append(profile.careerYears())
                        .append("\n희망 직무: ")
                        .append(String.join(", ", profile.jobFields()))
                        .append("\n보유 기술: ")
                        .append(String.join(", ", profile.haveSkills()))
                        .append("\n관심 기술: ")
                        .append(String.join(", ", profile.wantSkills()))
                        .append("\n\n[제목]\n")
                        .append(post.getTitle())
                        .append("\n\n[계열]\n")
                        .append(post.getField())
                        .append("\n\n[난이도]\n")
                        .append(post.getLevel())
                        .append("\n\n[본문 구간]");

        for (int index = 0; index < sections.size(); index++) {
            BlogPostSectionResponseDTO section = sections.get(index);
            input
                    .append("\n\n[")
                    .append(index + 1)
                    .append("]\n제목: ")
                    .append(section.heading())
                    .append("\n내용: ")
                    .append(section.contentText());
        }
        return input.toString();
    }

    private String loadPrompt() {
        try {
            return new ClassPathResource("prompts/guide-v1.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw aiUnavailable();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw aiUnavailable();
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw aiUnavailable();
        }
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private BusinessException aiUnavailable() {
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    private record GeneratedGuide(
            List<Integer> focusSections,
            List<SectionBadge> sectionBadges,
            String section1Text,
            String section2Text,
            String section3Text) {}

    private record GuideProfile(
            Long userId,
            Integer careerYears,
            List<String> jobFields,
            List<String> haveSkills,
            List<String> wantSkills,
            Integer profileVersion) {}
}

