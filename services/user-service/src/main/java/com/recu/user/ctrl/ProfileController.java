package com.recu.user.ctrl;

import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.domain.dto.ProfileRequestDTO;
import com.recu.user.domain.dto.ProfileResponseDTO;
import com.recu.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @PostMapping
  public ResponseEntity<?> setProfile(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @RequestBody ProfileRequestDTO request) {
    validateUserId(userId);
    ProfileResponseDTO response = profileService.setProfile(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public ResponseEntity<?> getProfile(
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    validateUserId(userId);
    ProfileResponseDTO response = profileService.getProfile(userId);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @PutMapping
  public ResponseEntity<?> updateProfile(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @RequestBody ProfileRequestDTO request) {
    validateUserId(userId);
    ProfileResponseDTO response = profileService.updateProfile(userId, request);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  private void validateUserId(Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
  }
}
