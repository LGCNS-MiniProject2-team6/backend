package com.recu.user.features.internal.profile.ctrl;

import com.recu.user.features.internal.profile.domain.dto.ProfileResponseDTO;
import com.recu.user.features.internal.profile.service.ProfileInternalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class ProfileInternalController {

    private final ProfileInternalService profileInternalService;

    public ProfileInternalController(ProfileInternalService profileInternalService) {
        this.profileInternalService = profileInternalService;
    }

    @GetMapping("/{userId}")
    public ProfileResponseDTO getProfile(@PathVariable Long userId) {
        return profileInternalService.getProfile(userId);
    }
}
