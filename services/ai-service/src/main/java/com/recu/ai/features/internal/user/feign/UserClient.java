package com.recu.ai.features.internal.user.feign;

import com.recu.ai.features.internal.user.domain.dto.ProfileResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/internal/users/{userId}")
    ProfileResponseDTO getProfile(@PathVariable("userId") Long userId);
}
