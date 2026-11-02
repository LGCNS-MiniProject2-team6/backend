package com.recu.ai.internal;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Authenticate the existing AI branch's BlogClient without changing its URLs or DTOs. */
@Configuration
public class BlogReadFeignConfig {
  @Bean
  RequestInterceptor blogReadAuthentication(
      @Value("${recu.internal.token:${INTERNAL_API_TOKEN:}}") String token) {
    return template -> {
      if (template.feignTarget() != null && "blog-service".equals(template.feignTarget().name())) {
        if (token.isBlank()) {
          throw new IllegalStateException("INTERNAL_API_TOKEN is required for Blog internal reads");
        }
        template.header("X-Internal-Token", token);
      }
    };
  }
}
