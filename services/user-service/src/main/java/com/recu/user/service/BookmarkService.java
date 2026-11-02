package com.recu.user.service;

import com.recu.user.client.BlogServiceClient;
import com.recu.user.client.BlogServiceClient.PostSummaryDTO;
import com.recu.user.client.BlogServiceClient.PostSummaryResponse;
import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.domain.dto.BookmarkResponseDTO;
import com.recu.user.domain.entity.BookmarkEntity;
import com.recu.user.domain.entity.UserEntity;
import com.recu.user.repository.BookmarkRepository;
import com.recu.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkService {

  private final BookmarkRepository bookmarkRepository;
  private final UserRepository userRepository;
  private final BlogServiceClient blogServiceClient;

  @Transactional
  public BookmarkResponseDTO addBookmark(Long userId, Long postId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (bookmarkRepository.existsByUser_UserIdAndBlogPostId(userId, postId)) {
      throw new BusinessException(ErrorCode.ALREADY_BOOKMARKED);
    }

    BookmarkEntity bookmark =
        BookmarkEntity.builder()
            .user(user)
            .blogPostId(postId)
            .createdAt(LocalDateTime.now())
            .build();

    BookmarkEntity saved = bookmarkRepository.save(bookmark);

    return BookmarkResponseDTO.builder()
        .bookmarkId(saved.getBookmarkId())
        .postId(saved.getBlogPostId())
        .createdAt(saved.getCreatedAt())
        .build();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getBookmark(Long userId) {
    userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    List<BookmarkEntity> bookmarks = bookmarkRepository.findByUser_UserId(userId);
    if (bookmarks.isEmpty()) {
      return Map.of("bookmarks", Collections.emptyList(), "count", 0);
    }

    List<Long> postIds = bookmarks.stream().map(BookmarkEntity::getBlogPostId).distinct().toList();

    Map<Long, PostSummaryDTO> postMap = fetchPostSummariesSafe(postIds);

    List<BookmarkResponseDTO> responseList = new ArrayList<>();
    for (BookmarkEntity bookmark : bookmarks) {
      Long postId = bookmark.getBlogPostId();
      OffsetDateTime savedAt = bookmark.getCreatedAt().atOffset(ZoneOffset.of("+09:00"));
      PostSummaryDTO post = postMap.get(postId);

      responseList.add(
          BookmarkResponseDTO.builder()
              .bookmarkId(bookmark.getBookmarkId())
              .postId(postId)
              .companyId(post != null ? post.companyId() : null)
              .title(post != null ? post.title() : "게시글 #" + postId)
              .companyName(post != null ? post.companyName() : "")
              .categories(post != null && post.categories() != null ? post.categories() : List.of())
              .summary(post != null ? post.summary() : null)
              .skills(post != null && post.skills() != null ? post.skills() : List.of())
              .publishedAt(post != null ? post.publishedAt() : null)
              .savedAt(savedAt)
              .hasGuide(false)
              .build());
    }

    return Map.of("bookmarks", responseList, "count", responseList.size());
  }

  @Transactional
  public void deleteBookmark(Long userId, Long postId) {
    BookmarkEntity bookmark =
        bookmarkRepository
            .findByUser_UserIdAndBlogPostId(userId, postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    bookmarkRepository.delete(bookmark);
  }

  private Map<Long, PostSummaryDTO> fetchPostSummariesSafe(List<Long> postIds) {
    try {
      PostSummaryResponse response =
          blogServiceClient.lookupPosts(new BlogServiceClient.PostIdsRequest(postIds));
      if (response != null && response.posts() != null) {
        return response.posts().stream()
            .collect(Collectors.toMap(PostSummaryDTO::postId, Function.identity(), (p1, p2) -> p1));
      }
    } catch (Exception e) {
      log.warn("Failed to lookup posts {} from blog-service: {}", postIds, e.getMessage());
    }
    return Collections.emptyMap();
  }
}
