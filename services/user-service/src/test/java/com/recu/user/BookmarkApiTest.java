package com.recu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.user.client.BlogServiceClient;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.BookmarkRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("standalone")
@Transactional
class BookmarkApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private BookmarkRepository bookmarkRepository;

  @MockBean private BlogServiceClient blogServiceClient;

  private UserEntity testUser;

  @BeforeEach
  void setUp() {
    testUser =
        userRepository.save(
            UserEntity.builder()
                .googleSub("bookmark-user-sub")
                .name("북마크유저")
                .careerYears(2)
                .profileVersion(1)
                .role("USER")
                .createdAt(LocalDate.now())
                .updatedAt(LocalDate.now())
                .build());

    given(blogServiceClient.getPost(eq(501L)))
        .willReturn(
            new BlogServiceClient.PostResponseDTO(
                501L,
                1L,
                "Spring Boot MSA 구축기",
                LocalDate.of(2026, 11, 1),
                "카카오",
                List.of("Backend"),
                "MSA 구축 사례 요약",
                List.of("Spring Boot", "Kafka"),
                "https://example.com/posts/501"));
  }

  @Test
  @DisplayName("북마크 추가(POST), 조회(GET), 삭제(DELETE) 검증")
  void bookmarkLifecycle_Success() throws Exception {
    // 1. 북마크 추가 (POST)
    mockMvc
        .perform(post("/api/bookmarks/501").header("X-User-Id", testUser.getUserId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.postId").value(501));

    assertThat(bookmarkRepository.existsByUser_UserIdAndBlogPostId(testUser.getUserId(), 501L))
        .isTrue();

    // 중복 북마크 시 409 Conflict 확인
    mockMvc
        .perform(post("/api/bookmarks/501").header("X-User-Id", testUser.getUserId()))
        .andExpect(status().isConflict());

    // 2. 북마크 조회 (GET) - BlogServiceClient 연동 매핑 검증
    mockMvc
        .perform(get("/api/bookmarks").header("X-User-Id", testUser.getUserId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.bookmarks[0].postId").value(501))
        .andExpect(jsonPath("$.bookmarks[0].title").value("Spring Boot MSA 구축기"))
        .andExpect(jsonPath("$.bookmarks[0].companyName").value("카카오"));

    // 3. 북마크 삭제 (DELETE)
    mockMvc
        .perform(delete("/api/bookmarks/501").header("X-User-Id", testUser.getUserId()))
        .andExpect(status().isNoContent());

    assertThat(bookmarkRepository.existsByUser_UserIdAndBlogPostId(testUser.getUserId(), 501L))
        .isFalse();
  }
}

