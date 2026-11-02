package com.recu.ai.internal;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.recu.ai.internal")
public class InternalBlogExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Error> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(
            new Error(
                e.getStatusCode().value() == 404 ? "NOT_FOUND" : "INVALID_INPUT",
                "내부 조회 요청을 처리할 수 없습니다.",
                null));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Error> invalid(Exception e) {
    return ResponseEntity.badRequest().body(new Error("INVALID_INPUT", "입력값이 올바르지 않습니다.", null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Error> unavailable(Exception e) {
    return ResponseEntity.status(503)
        .body(new Error("UPSTREAM_UNAVAILABLE", "데이터를 조회할 수 없습니다.", null));
  }

  public record Error(String code, String message, String field) {}
}
