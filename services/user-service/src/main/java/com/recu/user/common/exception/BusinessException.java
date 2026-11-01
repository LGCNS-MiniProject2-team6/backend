package com.recu.user.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String field;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.field = null;
  }

  public BusinessException(ErrorCode errorCode, String field) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.field = field;
  }
}
