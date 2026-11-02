package com.recu.user.service;

import com.recu.user.client.GoogleUserInfoClient;
import com.recu.user.client.GoogleUserInfoClient.GoogleUserInfo;
import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.common.token.JwtProvider;
import com.recu.user.common.token.JwtProvider.JwtTokenException;
import com.recu.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.domain.dto.UserResponseDTO;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final GoogleUserInfoClient googleUserInfoClient;
  private final UserRepository userRepository;
  private final JwtProvider jwtProvider;

  @Transactional
  public LoginResult signIn(String googleAccessToken) {
    GoogleUserInfo info = googleUserInfoClient.getUserInfo(googleAccessToken);

    UserEntity user = userRepository.findByGoogleSub(info.id()).orElseGet(() -> createUser(info));

    String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getRole());
    String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

    userRepository.updateRefreshToken(user.getUserId(), refreshToken);

    UserResponseDTO response =
        UserResponseDTO.builder()
            .userId(user.getUserId())
            .name(user.getName())
            .profileCompleted(user.getCareerYears() != null)
            .build();

    return new LoginResult(response, accessToken, refreshToken);
  }

  private UserEntity createUser(GoogleUserInfo info) {
    LocalDate now = LocalDate.now();

    return userRepository.save(
        UserEntity.builder()
            .googleSub(info.id())
            .name(info.name())
            .careerYears(null)
            .profileVersion(0)
            .role("USER")
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  public record LoginResult(UserResponseDTO response, String accessToken, String refreshToken) {}

  @Transactional
  public void signOut(Long userId, UserLogoutRequestDTO request) {
    String refreshToken = request.getRefreshToken();

    userRepository
        .findById(userId)
        .filter(user -> refreshToken.equals(user.getRefreshToken()))
        .ifPresentOrElse(
            user -> userRepository.updateRefreshToken(userId, null),
            () -> {
              throw new BusinessException(ErrorCode.UNAUTHORIZED);
            });
  }

  @Transactional
  public String refreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    Long userId;
    try {
      userId = jwtProvider.parseRefreshToken(refreshToken);
    } catch (JwtTokenException exception) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    UserEntity user =
        userRepository
            .findById(userId)
            .filter(found -> refreshToken.equals(found.getRefreshToken()))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

    return jwtProvider.createAccessToken(user.getUserId(), user.getRole());
  }
}
