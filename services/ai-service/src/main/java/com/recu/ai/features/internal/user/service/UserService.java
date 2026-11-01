package com.recu.ai.features.internal.user.service;

import com.recu.ai.features.internal.user.domain.dto.ProfileResponseDTO;
import com.recu.ai.features.internal.user.feign.UserClient;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserClient userClient;

    public UserService(UserClient userClient){
        this.userClient = userClient;
    }

    public ProfileResponseDTO getProfile(Long userId){
        return userClient.getProfile(userId);
    }

}
