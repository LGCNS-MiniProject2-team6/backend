package com.recu.ai.commons.handler;



import com.recu.ai.commons.exception.BusinessException;
import com.recu.ai.commons.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), exception.getField()));
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
        log.error("Unhandled exception", exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode, String field) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), field));
    }

    public record ErrorResponse(String code, String message, String field) {}
}

