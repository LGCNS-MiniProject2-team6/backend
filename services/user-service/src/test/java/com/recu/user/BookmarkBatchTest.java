package com.recu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.recu.user.client.BlogServiceClient;
import com.recu.user.domain.dto.BookmarkResponseDTO;
import com.recu.user.domain.entity.BookmarkEntity;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.BookmarkRepository;
import com.recu.user.repository.UserRepository;
import com.recu.user.service.BookmarkService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookmarkBatchTest {
  private final UserRepository users = mock(UserRepository.class);
  private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
  private final BlogServiceClient blog = mock(BlogServiceClient.class);
  private final BookmarkService service = new BookmarkService(bookmarks, users, blog);
  private final List<List<Long>> requests = new ArrayList<>();

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 200, 201, 450})
  void preservesAllPostDetailsAcrossBatches(int count) {
    prepareBookmarks(count);
    when(blog.lookupPosts(any()))
        .thenAnswer(
            invocation -> {
              BlogServiceClient.PostIdsRequest request = invocation.getArgument(0);
              requests.add(List.copyOf(request.postIds()));
              assertThat(request.postIds()).hasSizeLessThanOrEqualTo(200);
              var posts =
                  new ArrayList<>(request.postIds().stream().map(BookmarkBatchTest::post).toList());
              Collections.reverse(posts);
              return new BlogServiceClient.PostSummaryResponse(posts);
            });

    var response = service.getBookmark(7L);
    assertThat(response.get("count")).isEqualTo(count);
    var results = results(response.get("bookmarks"));
    assertThat(results).hasSize(count);
    for (int i = 0; i < count; i++) {
      long postId = i + 1;
      assertThat(results.get(i).getPostId()).isEqualTo(postId);
      assertThat(results.get(i).getTitle()).isEqualTo("Post " + postId);
      assertThat(results.get(i).getCompanyName()).isEqualTo("Company");
      assertThat(results.get(i).getSkills()).containsExactly("Java");
    }
    assertThat(requests).hasSize((count + 199) / 200);
    assertThat(requests.stream().flatMap(List::stream).toList())
        .containsExactlyElementsOf(LongStream.rangeClosed(1, count).boxed().toList());
    if (count == 0) verifyNoInteractions(blog);
  }

  @Test
  void retainsSuccessfulBatchesWhenOneBatchFails() {
    prepareBookmarks(450);
    when(blog.lookupPosts(any()))
        .thenAnswer(
            invocation -> {
              BlogServiceClient.PostIdsRequest request = invocation.getArgument(0);
              requests.add(List.copyOf(request.postIds()));
              if (request.postIds().get(0) == 201L)
                throw new IllegalStateException("Blog unavailable");
              return new BlogServiceClient.PostSummaryResponse(
                  request.postIds().stream().map(BookmarkBatchTest::post).toList());
            });

    var response = service.getBookmark(7L);
    var results = results(response.get("bookmarks"));
    assertThat(response.get("count")).isEqualTo(450);
    assertThat(requests).extracting(List::size).containsExactly(200, 200, 50);
    assertThat(results.get(199).getTitle()).isEqualTo("Post 200");
    assertThat(results.get(200).getTitle()).isEqualTo("게시글 #201");
    assertThat(results.get(200).getSkills()).isEmpty();
    assertThat(results.get(400).getTitle()).isEqualTo("Post 401");
    assertThat(results.get(449).getTitle()).isEqualTo("Post 450");
  }

  private void prepareBookmarks(int count) {
    var user = UserEntity.builder().userId(7L).build();
    when(users.findById(7L)).thenReturn(Optional.of(user));
    when(bookmarks.findByUser_UserId(7L))
        .thenReturn(
            LongStream.rangeClosed(1, count)
                .mapToObj(
                    id ->
                        BookmarkEntity.builder()
                            .user(user)
                            .blogPostId(id)
                            .createdAt(LocalDateTime.of(2026, 11, 2, 12, 0))
                            .build())
                .toList());
  }

  private static BlogServiceClient.PostSummaryDTO post(Long id) {
    return new BlogServiceClient.PostSummaryDTO(
        id,
        1L,
        "Post " + id,
        "Company",
        "Summary",
        List.of("Backend"),
        List.of("Java"),
        LocalDate.of(2026, 11, 2),
        "https://example.com/" + id);
  }

  @SuppressWarnings("unchecked")
  private static List<BookmarkResponseDTO> results(Object value) {
    return (List<BookmarkResponseDTO>) value;
  }
}
