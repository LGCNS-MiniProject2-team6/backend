package com.recu.platform.gateway.filter;

import com.recu.platform.gateway.exception.BusinessException;
import com.recu.platform.gateway.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** Authorization 헤더의 JWT 서명, 만료, 토큰 종류를 확인한다. */
@Component
public class JwtValidationGatewayFilterFactory
    extends AbstractGatewayFilterFactory<JwtValidationGatewayFilterFactory.Config> {

  static final String PARSED_CLAIMS_ATTRIBUTE =
      JwtValidationGatewayFilterFactory.class.getName() + ".claims";
  private static final String BEARER_PREFIX = "Bearer ";
  private final SecretKey signingKey;

  public JwtValidationGatewayFilterFactory(
      @Value(
              "${jwt.secret:default-secret-key-must-be-very-long-and-secure-at-least-256-bits-for-hmac-sha}")
          String secret) {
    super(Config.class);
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public static class Config {}

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
      if (authorization == null) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      }

      if (!authorization.startsWith(BEARER_PREFIX)
          || authorization.substring(BEARER_PREFIX.length()).isBlank()) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      }

      Claims claims;
      try {
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        claims =
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
      } catch (ExpiredJwtException exception) {
        throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
      } catch (RuntimeException exception) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      }

      try {
        if (claims.getSubject() == null
            || claims.getSubject().isBlank()
            || !"access".equals(claims.get("type"))) {
          throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
      } catch (RuntimeException exception) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      }
      exchange.getAttributes().put(PARSED_CLAIMS_ATTRIBUTE, claims);
      return chain.filter(exchange);
    };
  }
}
