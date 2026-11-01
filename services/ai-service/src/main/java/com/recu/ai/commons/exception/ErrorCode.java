package com.recu.ai.commons.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Google 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.FORBIDDEN, "유효하지 않은 Refresh 토큰입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "프로필 등록이 필요합니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    ALREADY_BOOKMARKED(HttpStatus.CONFLICT, "이미 북마크한 게시글입니다."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서비스를 사용할 수 없습니다."),
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "연결된 서비스를 사용할 수 없습니다."),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "아직 구현되지 않은 기능입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 프로필입니다.");

    private final HttpStatus status;
    private final String message;

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }
}

