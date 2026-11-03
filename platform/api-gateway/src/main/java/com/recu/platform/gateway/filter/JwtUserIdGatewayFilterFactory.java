package com.recu.platform.gateway.filter;

import com.recu.platform.gateway.exception.BusinessException;
import com.recu.platform.gateway.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/** 검증된 JWT의 subject(userId)를 내부 서비스 전달용 userId 헤더에 담는다. */
@Component
public class JwtUserIdGatewayFilterFactory
    extends AbstractGatewayFilterFactory<JwtUserIdGatewayFilterFactory.Config> {

  public JwtUserIdGatewayFilterFactory() {
    super(Config.class);
  }

  public static class Config {}

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      String userId;
      try {
        Claims claims =
            exchange.getAttribute(JwtValidationGatewayFilterFactory.PARSED_CLAIMS_ATTRIBUTE);
        if (claims == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
          throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userId = claims.getSubject();
      } catch (RuntimeException exception) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      }

      var request =
          exchange.getRequest().mutate().headers(headers -> headers.set("userId", userId)).build();
      return chain.filter(exchange.mutate().request(request).build());
    };
  }
}
