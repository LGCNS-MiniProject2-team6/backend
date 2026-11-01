package com.recu.user.common.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT를 생성하고 서명·만료·claim을 검증한다. */
@Component
public class JwtProvider {

  private static final String ROLE_CLAIM = "role";
  private static final String TYPE_CLAIM = "type";
  private static final String ACCESS_TYPE = "access";
  private static final String REFRESH_TYPE = "refresh";
  private static final String USER_ROLE = "USER";
  private static final String ADMIN_ROLE = "ADMIN";

  private final Key signingKey;
  private final long accessExpiryMs;
  private final long refreshExpiryMs;

  public JwtProvider(
      @Value("${jwt.secret:default-secret-key-must-be-very-long-and-secure-at-least-256-bits-for-hmac-sha}")
          String secret,
      @Value("${jwt.access-expiry-ms:86400000}") long accessExpiryMs,
      @Value("${jwt.refresh-expiry-ms:604800000}") long refreshExpiryMs) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpiryMs = accessExpiryMs;
    this.refreshExpiryMs = refreshExpiryMs;
  }

  public String createAccessToken(Long userId, String role) {
    requireUserId(userId);
    requireAccessRole(role);

    Date issuedAt = new Date();
    Date expiration = new Date(issuedAt.getTime() + accessExpiryMs);

    return Jwts.builder()
        .setSubject(userId.toString())
        .claim(ROLE_CLAIM, role)
        .claim(TYPE_CLAIM, ACCESS_TYPE)
        .setIssuedAt(issuedAt)
        .setExpiration(expiration)
        .signWith(signingKey)
        .compact();
  }

  public String createRefreshToken(Long userId) {
    requireUserId(userId);

    Date issuedAt = new Date();
    Date expiration = new Date(issuedAt.getTime() + refreshExpiryMs);

    return Jwts.builder()
        .setSubject(userId.toString())
        .claim(TYPE_CLAIM, REFRESH_TYPE)
        .setIssuedAt(issuedAt)
        .setExpiration(expiration)
        .signWith(signingKey)
        .compact();
  }

  public ParsedToken parse(String token) {
    try {
      Claims claims =
          Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();

      Long userId = Long.valueOf(claims.getSubject());
      String role = claims.get(ROLE_CLAIM, String.class);
      String type = claims.get(TYPE_CLAIM, String.class);

      requireTokenType(type);
      if (ACCESS_TYPE.equals(type)) {
        requireAccessRole(role);
      }

      return new ParsedToken(userId, role, type);
    } catch (ExpiredJwtException exception) {
      throw JwtTokenException.tokenExpired(exception);
    } catch (JwtException | IllegalArgumentException exception) {
      throw JwtTokenException.unauthorized(exception);
    }
  }

  public Long parseRefreshToken(String token) {
    ParsedToken parsedToken = parse(token);

    if (!REFRESH_TYPE.equals(parsedToken.type())) {
      throw JwtTokenException.unauthorized();
    }

    return parsedToken.userId();
  }

  private void requireUserId(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("userId는 필수입니다.");
    }
  }

  private void requireAccessRole(String role) {
    if (!USER_ROLE.equals(role) && !ADMIN_ROLE.equals(role)) {
      throw new IllegalArgumentException("role은 USER 또는 ADMIN이어야 합니다.");
    }
  }

  private void requireTokenType(String type) {
    if (!ACCESS_TYPE.equals(type) && !REFRESH_TYPE.equals(type)) {
      throw new IllegalArgumentException("지원하지 않는 JWT type입니다.");
    }
  }

  public record ParsedToken(Long userId, String role, String type) {}

  /** JWT 검증 실패를 전달하는 최소 예외 타입이다. */
  public static class JwtTokenException extends RuntimeException {

    private final Code code;

    private JwtTokenException(Code code, Throwable cause) {
      super(code.message(), cause);
      this.code = code;
    }

    private JwtTokenException(Code code) {
      super(code.message());
      this.code = code;
    }

    public static JwtTokenException tokenExpired(Throwable cause) {
      return new JwtTokenException(Code.TOKEN_EXPIRED, cause);
    }

    public static JwtTokenException unauthorized(Throwable cause) {
      return new JwtTokenException(Code.UNAUTHORIZED, cause);
    }

    public static JwtTokenException unauthorized() {
      return new JwtTokenException(Code.UNAUTHORIZED);
    }

    public Code getCode() {
      return code;
    }

    public enum Code {
      TOKEN_EXPIRED("JWT 토큰이 만료되었습니다."),
      UNAUTHORIZED("유효하지 않은 인증 정보입니다.");

      private final String message;

      Code(String message) {
        this.message = message;
      }

      public String message() {
        return message;
      }
    }
  }
}
