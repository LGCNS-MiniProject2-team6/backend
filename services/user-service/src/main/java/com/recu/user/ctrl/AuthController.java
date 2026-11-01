package com.recu.user.ctrl;

import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.domain.dto.UserLoginRequestDTO;
import com.recu.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  @PostMapping("/google")
  public ResponseEntity<?> signIn(@RequestBody UserLoginRequestDTO request) {
    if (request == null || request.getGoogleAccessToken() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "googleAccessToken");
    }

    UserService.LoginResult result = userService.signIn(request.getGoogleAccessToken());

    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
        .header("Refresh-Token", result.refreshToken())
        .body(result.response());
  }

  @PostMapping("/logout")
  public ResponseEntity<?> signOut(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody UserLogoutRequestDTO request) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    userService.signOut(userId, request);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refreshToken(
      @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
    String accessToken = userService.refreshToken(refreshToken);

    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .build();
  }
}
