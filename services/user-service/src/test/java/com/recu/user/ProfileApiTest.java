package com.recu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.user.client.BlogServiceClient;
import com.recu.user.domain.dto.ProfileRequestDTO;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("standalone")
@Transactional
class ProfileApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  @MockBean private BlogServiceClient blogServiceClient;

  private UserEntity testUser;

  @BeforeEach
  void setUp() {
    testUser =
        userRepository.save(
            UserEntity.builder()
                .googleSub("profile-test-sub")
                .name("프로필유저")
                .careerYears(null)
                .profileVersion(0)
                .role("USER")
                .createdAt(LocalDate.now())
                .updatedAt(LocalDate.now())
                .build());

    given(blogServiceClient.getTechTags())
        .willReturn(
            new BlogServiceClient.TechResponseDTO(
                List.of(
                    new BlogServiceClient.TechResponseDTO.TechTagDTO(101L, "Java", "Backend"),
                    new BlogServiceClient.TechResponseDTO.TechTagDTO(102L, "Spring", "Backend"),
                    new BlogServiceClient.TechResponseDTO.TechTagDTO(201L, "React", "Frontend"))));
  }

  @Test
  @DisplayName("프로필 등록(POST), 조회(GET), 수정(PUT) 전체 라이프사이클 검증")
  void profileLifecycle_Success() throws Exception {
    ProfileRequestDTO request =
        ProfileRequestDTO.builder()
            .jobFields(List.of("Backend", "DevOps"))
            .careerYears(3)
            .haveSkillIds(List.of(101L, 102L))
            .wantSkillIds(List.of(201L))
            .build();

    // 1. 등록 (POST)
    mockMvc
        .perform(
            post("/api/profile")
                .header("X-User-Id", testUser.getUserId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("프로필유저"))
        .andExpect(jsonPath("$.careerYears").value(3))
        .andExpect(jsonPath("$.profileVersion").value(1))
        .andExpect(jsonPath("$.haveSkills[0].name").value("Java"))
        .andExpect(jsonPath("$.wantSkills[0].name").value("React"));

    // 2. 조회 (GET)
    mockMvc
        .perform(get("/api/profile").header("X-User-Id", testUser.getUserId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.careerYears").value(3))
        .andExpect(jsonPath("$.profileVersion").value(1));

    // 3. 수정 (PUT)
    ProfileRequestDTO updateRequest =
        ProfileRequestDTO.builder()
            .jobFields(List.of("Backend"))
            .careerYears(4)
            .haveSkillIds(List.of(101L))
            .wantSkillIds(List.of(201L))
            .build();

    mockMvc
        .perform(
            put("/api/profile")
                .header("X-User-Id", testUser.getUserId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.careerYears").value(4))
        .andExpect(jsonPath("$.profileVersion").value(2));

    UserEntity updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
    assertThat(updatedUser.getCareerYears()).isEqualTo(4);
    assertThat(updatedUser.getProfileVersion()).isEqualTo(2);
  }
}

