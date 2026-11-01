package com.recu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.user.client.GoogleUserInfoClient;
import com.recu.user.client.GoogleUserInfoClient.GoogleUserInfo;
import com.recu.user.domain.dto.UserLoginRequestDTO;
import com.recu.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("standalone")
@Transactional
class AuthApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  @MockBean private GoogleUserInfoClient googleUserInfoClient;

  @Test
  @DisplayName("구글 OAuth 로그인 성공 시 토큰 발급 및 신규 유저 생성 검증")
  void googleLogin_Success() throws Exception {
    given(googleUserInfoClient.getUserInfo(anyString()))
        .willReturn(new GoogleUserInfo("google-sub-123", "테스터", "test@example.com"));

    UserLoginRequestDTO request = new UserLoginRequestDTO("valid-google-token");

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.AUTHORIZATION))
        .andExpect(header().exists("Refresh-Token"))
        .andExpect(jsonPath("$.name").value("테스터"))
        .andExpect(jsonPath("$.profileCompleted").value(false));

    assertThat(userRepository.findByGoogleSub("google-sub-123")).isPresent();
  }

  @Test
  @DisplayName("Refresh 토큰으로 Access 토큰 재발급 검증")
  void refreshToken_Success() throws Exception {
    given(googleUserInfoClient.getUserInfo(anyString()))
        .willReturn(new GoogleUserInfo("google-sub-456", "재발급테스터", "refresh@example.com"));

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new UserLoginRequestDTO("google-token-456"))))
            .andReturn();

    String refreshToken = loginResult.getResponse().getHeader("Refresh-Token");

    mockMvc
        .perform(post("/api/auth/refresh").header("Refresh-Token", refreshToken))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.AUTHORIZATION));
  }

  @Test
  @DisplayName("로그아웃 성공 시 DB의 refresh_token 무효화 검증")
  void logout_Success() throws Exception {
    given(googleUserInfoClient.getUserInfo(anyString()))
        .willReturn(new GoogleUserInfo("google-sub-789", "로그아웃테스터", "logout@example.com"));

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new UserLoginRequestDTO("google-token-789"))))
            .andReturn();

    String refreshToken = loginResult.getResponse().getHeader("Refresh-Token");
    UserEntity user = userRepository.findByGoogleSub("google-sub-789").orElseThrow();

    UserLogoutRequestDTO logoutRequest = new UserLogoutRequestDTO(refreshToken);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("X-User-Id", user.getUserId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
        .andExpect(status().isNoContent());

    UserEntity afterUser = userRepository.findById(user.getUserId()).orElseThrow();
    assertThat(afterUser.getRefreshToken()).isNull();
  }
}

