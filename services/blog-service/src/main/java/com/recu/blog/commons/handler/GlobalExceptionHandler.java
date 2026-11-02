package com.recu.blog.commons.handler;

import com.recu.blog.commons.exception.*;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorResponse> business(BusinessException e) {
    return response(e.getErrorCode(), e.getField());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
    return response(
        ErrorCode.INVALID_INPUT,
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField())
            .findFirst()
            .orElse(null));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> invalid(Exception e) {
    return response(ErrorCode.INVALID_INPUT, null);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> header(MissingRequestHeaderException e) {
    return response(ErrorCode.UNAUTHORIZED, e.getHeaderName());
  }

  @ExceptionHandler(FeignException.class)
  ResponseEntity<ErrorResponse> upstream(FeignException e) {
    return response(ErrorCode.UPSTREAM_UNAVAILABLE, null);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ErrorResponse> internalStatus(ResponseStatusException e) {
    ErrorCode code =
        e.getStatusCode().value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(e.getStatusCode())
        .body(new ErrorResponse(code.getCode(), code.getMessage(), null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception e) {
    log.error("Blog request failed: {}", e.getClass().getSimpleName());
    return response(ErrorCode.INTERNAL_ERROR, null);
  }

  private ResponseEntity<ErrorResponse> response(ErrorCode code, String field) {
    return ResponseEntity.status(code.getStatus())
        .body(new ErrorResponse(code.getCode(), code.getMessage(), field));
  }

  public record ErrorResponse(String code, String message, String field) {}
}
