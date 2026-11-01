package com.recu.blog.client;

import com.recu.blog.commons.exception.BusinessException;
import com.recu.blog.commons.exception.ErrorCode;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Explicit short timeouts and no automatic retry; never turn an outage into a false bookmark. */
public class InternalFeignConfig {
  @Bean
  RequestInterceptor internalToken(@Value("${recu.internal.token:}") String token) {
    return template -> {
      if (token.isBlank()) throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
      template.header("X-Internal-Token", token);
    };
  }

  @Bean
  Request.Options internalTimeouts() {
    return new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, false);
  }

  @Bean
  Retryer internalRetryer() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  ErrorDecoder internalErrors() {
    return (method, response) ->
        new BusinessException(
            response.status() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.UPSTREAM_UNAVAILABLE);
  }
}
