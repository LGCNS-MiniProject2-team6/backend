package com.recu.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.user.client.BlogServiceClient;
import com.recu.user.domain.entity.NotificationEntity;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.NotificationRepository;
import com.recu.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("standalone")
@Transactional
class NotificationApiTest {
  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private NotificationRepository notifications;
  @MockitoBean private BlogServiceClient blog;

  @Test
  void listsOwnedNotificationsWithStablePaginationAndGlobalUnreadCount() throws Exception {
    var owner = user("notification-owner");
    var other = user("notification-other");
    var first = notification(owner, "first", 11L);
    var second = notification(owner, "second", 12L);
    notification(other, "foreign", 13L);
    given(blog.lookupPosts(any()))
        .willReturn(
            new BlogServiceClient.PostSummaryResponse(
                List.of(
                    new BlogServiceClient.PostSummaryDTO(
                        12L, 42L, "second", null, null, null, null, null, null))));

    mvc.perform(get("/api/notifications").header("userId", owner.getUserId()).param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications.length()").value(1))
        .andExpect(jsonPath("$.notifications[0].id").value(second.getNotificationId()))
        .andExpect(jsonPath("$.notifications[0].companyId").value(42))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.unreadCount").value(2));
    mvc.perform(
            get("/api/notifications")
                .header("userId", owner.getUserId())
                .param("size", "1")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications[0].id").value(first.getNotificationId()))
        .andExpect(jsonPath("$.unreadCount").value(2));
  }

  @Test
  void readIsIdempotentAndOnlyAvailableToTheOwner() throws Exception {
    var owner = user("read-owner");
    var other = user("read-other");
    var row = notification(owner, "read-event", 21L);
    var path = "/api/notifications/" + row.getNotificationId() + "/read";
    mvc.perform(patch(path)).andExpect(status().isUnauthorized());
    mvc.perform(patch(path).header("userId", other.getUserId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(patch("/api/notifications/999999/read").header("userId", owner.getUserId()))
        .andExpect(status().isNotFound());
    for (int i = 0; i < 2; i++) {
      mvc.perform(patch(path).header("userId", owner.getUserId()))
          .andExpect(status().isNoContent());
    }
    mvc.perform(get("/api/notifications").header("userId", owner.getUserId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications[0].isRead").value(true))
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  void emptyListAndInvalidPaginationHaveDefinedResponses() throws Exception {
    var owner = user("empty-owner");
    mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notifications").header("userId", owner.getUserId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications").isEmpty())
        .andExpect(jsonPath("$.unreadCount").value(0));
    mvc.perform(get("/api/notifications").header("userId", owner.getUserId()).param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/notifications").header("userId", owner.getUserId()).param("page", "abc"))
        .andExpect(status().isBadRequest());
  }

  private UserEntity user(String sub) {
    return users.save(
        UserEntity.builder()
            .googleSub(sub)
            .name("테스트")
            .role("USER")
            .profileVersion(1)
            .createdAt(LocalDate.now())
            .updatedAt(LocalDate.now())
            .build());
  }

  private NotificationEntity notification(UserEntity owner, String event, long postId) {
    return notifications.save(
        NotificationEntity.builder()
            .user(owner)
            .eventId(event)
            .blogPostId(postId)
            .title(event)
            .url("https://example.com/post")
            .createdAt(LocalDateTime.of(2026, 11, 3, 12, 0))
            .build());
  }
}
