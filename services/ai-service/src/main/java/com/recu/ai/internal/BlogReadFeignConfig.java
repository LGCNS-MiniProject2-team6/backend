package com.recu.ai.internal;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the shared service token to Feign calls into Blog and User internal endpoints. */
@Configuration
public class BlogReadFeignConfig {
  @Bean
  RequestInterceptor internalServiceAuthentication(
      @Value("${recu.internal.token:${INTERNAL_API_TOKEN:}}") String token) {
    return template -> {
      String target = template.feignTarget() == null ? "" : template.feignTarget().name();
      if ("blog-service".equals(target) || "user-service".equals(target)) {
        if (token.isBlank()) {
          throw new IllegalStateException(
              "INTERNAL_API_TOKEN is required for internal service calls");
        }
        template.header("X-Internal-Token", token);
      }
    };
  }
}
