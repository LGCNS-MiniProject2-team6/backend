package com.recu.user.service;

import com.recu.user.client.BlogServiceClient;
import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.domain.dto.ProfileRequestDTO;
import com.recu.user.domain.dto.ProfileResponseDTO;
import com.recu.user.domain.dto.ProfileResponseDTO.SkillResponseDTO;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.domain.entity.UserJobFieldEntity;
import com.recu.user.domain.entity.UserSkillEntity;
import com.recu.user.domain.entity.UserSkillPk;
import com.recu.user.repository.UserJobFieldRepository;
import com.recu.user.repository.UserRepository;
import com.recu.user.repository.UserSkillRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

  private final UserRepository userRepository;
  private final UserJobFieldRepository userJobFieldRepository;
  private final UserSkillRepository userSkillRepository;
  private final BlogServiceClient blogServiceClient;

  @Transactional
  public ProfileResponseDTO setProfile(Long userId, ProfileRequestDTO request) {
    validateProfileRequest(request);

    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (user.getProfileVersion() != null && user.getProfileVersion() != 0) {
      throw new BusinessException(ErrorCode.PROFILE_ALREADY_EXISTS);
    }

    user.setCareerYears(request.getCareerYears());
    user.setProfileVersion(1);
    user.setUpdatedAt(LocalDate.now());
    userRepository.save(user);

    saveJobFields(user, request.getJobFields());
    saveSkills(user, request.getHaveSkillIds(), "HAVE");
    saveSkills(user, request.getWantSkillIds(), "WANT");

    return buildProfileResponse(user, request.getJobFields(), request.getCareerYears(), 1);
  }

  @Transactional(readOnly = true)
  public ProfileResponseDTO getProfile(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (user.getCareerYears() == null) {
      throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
    }

    List<String> jobFields =
        userJobFieldRepository.findAllByUser(user).stream()
            .map(UserJobFieldEntity::getJobField)
            .toList();

    return buildProfileResponse(user, jobFields, user.getCareerYears(), user.getProfileVersion());
  }

  @Transactional
  public ProfileResponseDTO updateProfile(Long userId, ProfileRequestDTO request) {
    validateProfileRequest(request);

    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (user.getCareerYears() == null) {
      throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
    }

    int nextVersion = user.getProfileVersion() == null ? 1 : user.getProfileVersion() + 1;
    user.setCareerYears(request.getCareerYears());
    user.setProfileVersion(nextVersion);
    user.setUpdatedAt(LocalDate.now());
    userRepository.save(user);

    userJobFieldRepository.deleteAllByUser(user);
    saveJobFields(user, request.getJobFields());

    userSkillRepository.deleteAllByUser(user);
    saveSkills(user, request.getHaveSkillIds(), "HAVE");
    saveSkills(user, request.getWantSkillIds(), "WANT");

    return buildProfileResponse(
        user, request.getJobFields(), request.getCareerYears(), nextVersion);
  }

  private void saveJobFields(UserEntity user, List<String> jobFields) {
    if (jobFields == null || jobFields.isEmpty()) return;
    List<UserJobFieldEntity> entities =
        jobFields.stream()
            .map(jobField -> UserJobFieldEntity.builder().user(user).jobField(jobField).build())
            .toList();
    userJobFieldRepository.saveAll(entities);
  }

  private void saveSkills(UserEntity user, List<Long> skillIds, String skillType) {
    if (skillIds == null || skillIds.isEmpty()) return;
    List<UserSkillEntity> entities =
        skillIds.stream()
            .map(
                skillId ->
                    UserSkillEntity.builder()
                        .id(new UserSkillPk(user.getUserId(), skillId))
                        .user(user)
                        .skillType(skillType)
                        .build())
            .toList();
    userSkillRepository.saveAll(entities);
  }

  private ProfileResponseDTO buildProfileResponse(
      UserEntity user, List<String> jobFields, Integer careerYears, int profileVersion) {
    Map<Long, String> techTagNameMap = fetchTechTagNameMap();

    List<SkillResponseDTO> haveSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "HAVE").stream()
            .map(
                skill -> {
                  Long tagId = skill.getId().getTechTagId();
                  String tagName = techTagNameMap.getOrDefault(tagId, "TechTag-" + tagId);
                  return new SkillResponseDTO(tagId, tagName);
                })
            .toList();

    List<SkillResponseDTO> wantSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "WANT").stream()
            .map(
                skill -> {
                  Long tagId = skill.getId().getTechTagId();
                  String tagName = techTagNameMap.getOrDefault(tagId, "TechTag-" + tagId);
                  return new SkillResponseDTO(tagId, tagName);
                })
            .toList();

    return ProfileResponseDTO.builder()
        .userId(user.getUserId())
        .name(user.getName())
        .jobFields(jobFields)
        .careerYears(careerYears)
        .haveSkills(haveSkills)
        .wantSkills(wantSkills)
        .profileVersion(profileVersion)
        .build();
  }

  private Map<Long, String> fetchTechTagNameMap() {
    try {
      BlogServiceClient.TechResponseDTO techResponse = blogServiceClient.getTechTags();
      if (techResponse != null && techResponse.techTags() != null) {
        return techResponse.techTags().stream()
            .collect(
                Collectors.toMap(
                    BlogServiceClient.TechResponseDTO.TechTagDTO::techTagId,
                    BlogServiceClient.TechResponseDTO.TechTagDTO::name,
                    (existing, replacement) -> existing));
      }
    } catch (Exception e) {
      log.warn(
          "Failed to fetch tech tags from blog-service, fallback to tagId name: {}",
          e.getMessage());
    }
    return Collections.emptyMap();
  }

  private void validateProfileRequest(ProfileRequestDTO request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    if (request.getCareerYears() == null || request.getCareerYears() < 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "careerYears");
    }
    if (request.getJobFields() == null || request.getJobFields().isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "jobFields");
    }
    if (request.getHaveSkillIds() == null || request.getHaveSkillIds().isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "haveSkillIds");
    }
    if (request.getWantSkillIds() == null || request.getWantSkillIds().isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "wantSkillIds");
    }
  }
}
