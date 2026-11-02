package com.recu.platform.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Slf4j
@Order(-2)
@RestControllerAdvice(basePackages = "com.recu.platform.gateway")
public class GlobalExceptionHandler implements WebExceptionHandler {

  private final ObjectMapper objectMapper;

  public GlobalExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
    ResponseEntity<ErrorResponse> responseEntity;
    if (exception instanceof BusinessException businessException) {
      responseEntity = handleBusinessException(businessException);
    } else if (exception instanceof ResponseStatusException statusException) {
      responseEntity = handleResponseStatusException(statusException);
    } else if (exception instanceof MethodArgumentNotValidException validationException) {
      responseEntity = handleInvalidInput(validationException);
    } else if (exception instanceof Exception handledException) {
      responseEntity = handleException(handledException);
    } else {
      return Mono.error(exception);
    }
    return writeResponse(exchange.getResponse(), responseEntity);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), exception.getField()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatusException(
      ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(
            new ErrorResponse(
                exception.getStatusCode().value() == 404 ? "NOT_FOUND" : "INVALID_INPUT",
                exception.getReason() != null ? exception.getReason() : "요청을 처리할 수 없습니다.",
                null));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidInput(
      MethodArgumentNotValidException exception) {
    String field =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getField)
            .orElse(null);
    return errorResponse(ErrorCode.INVALID_INPUT, field);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception exception) {
    log.error("Unhandled exception in api-gateway", exception);
    return errorResponse(ErrorCode.INTERNAL_ERROR, null);
  }

  private ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode, String field) {
    return ResponseEntity.status(errorCode.getStatus())
        .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), field));
  }

  private Mono<Void> writeResponse(
      ServerHttpResponse response, ResponseEntity<ErrorResponse> responseEntity) {
    response.setStatusCode(responseEntity.getStatusCode());
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      byte[] body = objectMapper.writeValueAsBytes(responseEntity.getBody());
      return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    } catch (JsonProcessingException exception) {
      return Mono.error(exception);
    }
  }

  public record ErrorResponse(String code, String message, String field) {}
}
