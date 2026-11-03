package com.recu.user.ctrl;

import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import com.recu.user.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.recu.user.domain.dto.NotificationPageResponse;
import com.recu.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationService notificationService;

  @GetMapping
  public NotificationPageResponse list(
      @RequestHeader(value = "userId", required = false) Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireUser(userId);
    if (page < 0 || size < 1 || size > 100) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    return notificationService.list(userId, page, size);
  }

  @PatchMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(
      @RequestHeader(value = "userId", required = false) Long userId, @PathVariable Long id) {
    requireUser(userId);
    notificationService.markRead(userId, id);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidParameter(
      MethodArgumentTypeMismatchException exception) {
    var error = ErrorCode.INVALID_INPUT;
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(error.getCode(), error.getMessage(), exception.getName()));
  }

  private void requireUser(Long userId) {
    if (userId == null || userId <= 0) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
  }
}
