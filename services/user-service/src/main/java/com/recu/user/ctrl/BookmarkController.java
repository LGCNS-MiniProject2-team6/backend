package com.recu.user.ctrl;

import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.domain.dto.BookmarkResponseDTO;
import com.recu.user.service.BookmarkService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

  private final BookmarkService bookmarkService;

  @PostMapping("/{postId}")
  public ResponseEntity<?> addBookmark(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable("postId") Long postId) {
    validateUserId(userId);
    BookmarkResponseDTO result = bookmarkService.addBookmark(userId, postId);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping
  public ResponseEntity<?> getBookmark(
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    validateUserId(userId);
    Map<String, Object> result = bookmarkService.getBookmark(userId);
    return ResponseEntity.ok(result);
  }

  @DeleteMapping("/{postId}")
  public ResponseEntity<?> deleteBookmark(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable("postId") Long postId) {
    validateUserId(userId);
    bookmarkService.deleteBookmark(userId, postId);
    return ResponseEntity.noContent().build();
  }

  private void validateUserId(Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
  }
}

