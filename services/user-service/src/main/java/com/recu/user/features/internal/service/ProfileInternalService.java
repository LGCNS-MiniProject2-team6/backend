package com.recu.user.features.internal.service;

import com.recu.user.domain.entity.UserEntity;
import com.recu.user.domain.entity.UserJobFieldEntity;
import com.recu.user.domain.entity.UserSkillEntity;
import com.recu.user.features.internal.domain.dto.ProfileResponseDTO;
import com.recu.user.repository.UserJobFieldRepository;
import com.recu.user.repository.UserRepository;
import com.recu.user.repository.UserSkillRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileInternalService {

  private final UserRepository userRepository;
  private final UserJobFieldRepository jobFieldRepository;
  private final UserSkillRepository userSkillRepository;

  public ProfileInternalService(
      UserRepository userRepository,
      UserJobFieldRepository jobFieldRepository,
      UserSkillRepository userSkillRepository) {
    this.userRepository = userRepository;
    this.jobFieldRepository = jobFieldRepository;
    this.userSkillRepository = userSkillRepository;
  }

  @Transactional(readOnly = true)
  public ProfileResponseDTO getProfile(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    List<String> jobFields =
        jobFieldRepository.findProfileJobFieldsByUserId(userId).stream()
            .map(UserJobFieldEntity::getJobField)
            .toList();

    List<UserSkillEntity> skills = userSkillRepository.findProfileSkillsByUserId(userId);
    List<Long> haveTechTagIds =
        skills.stream()
            .filter(skill -> "HAVE".equalsIgnoreCase(skill.getSkillType()))
            .map(skill -> skill.getId().getTechTagId())
            .toList();
    List<Long> wantTechTagIds =
        skills.stream()
            .filter(skill -> "WANT".equalsIgnoreCase(skill.getSkillType()))
            .map(skill -> skill.getId().getTechTagId())
            .toList();

    return new ProfileResponseDTO(
        user.getName(),
        user.getUserId(),
        jobFields,
        user.getCareerYears(),
        haveTechTagIds,
        wantTechTagIds,
        user.getProfileVersion());
  }
}
