package com.recu.ai.commons.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    // 필드명이 필요없는 오류용 생성자
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    // 필드명이 필요한 오류용 생성자
    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.field = field;
    }
}