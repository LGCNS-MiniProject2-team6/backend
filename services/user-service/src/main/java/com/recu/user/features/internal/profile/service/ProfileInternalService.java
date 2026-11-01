package com.recu.user.features.internal.profile.service;

import com.recu.user.features.internal.profile.domain.dto.ProfileResponseDTO;
import com.recu.user.features.internal.profile.domain.entity.AppUserEntity;
import com.recu.user.features.internal.profile.domain.entity.UserSkillEntity;
import com.recu.user.features.internal.profile.repository.AppUserRepository;
import com.recu.user.features.internal.profile.repository.UserJobFieldRepository;
import com.recu.user.features.internal.profile.repository.UserSkillRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileInternalService {

    private final AppUserRepository appUserRepository;
    private final UserJobFieldRepository jobFieldRepository;
    private final UserSkillRepository userSkillRepository;

    public ProfileInternalService(
            AppUserRepository appUserRepository,
            UserJobFieldRepository jobFieldRepository,
            UserSkillRepository userSkillRepository) {
        this.appUserRepository = appUserRepository;
        this.jobFieldRepository = jobFieldRepository;
        this.userSkillRepository = userSkillRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfile(Long userId) {
        AppUserEntity user =
                appUserRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<String> jobFields =
                jobFieldRepository.findByUserId(userId).stream()
                        .map(jobField -> jobField.getJobField())
                        .toList();

        List<UserSkillEntity> skills = userSkillRepository.findProfileSkills(userId);
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
