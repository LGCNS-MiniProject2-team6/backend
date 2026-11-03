package com.recu.user.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlogServiceClientConfig {

  @Bean
  RequestInterceptor blogInternalAuthInterceptor(
      @Value("${recu.internal.token:${INTERNAL_API_TOKEN:}}") String token) {
    return template -> {
      if (!template.url().startsWith("/internal/")) {
        return;
      }
      if (token == null || token.isBlank()) {
        throw new IllegalStateException("INTERNAL_API_TOKEN is required for Blog internal calls");
      }
      template.header("X-Internal-Token", token);
    };
  }
}
