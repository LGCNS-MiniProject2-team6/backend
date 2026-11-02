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
      if (template.feignTarget() != null && "blog-service".equals(template.feignTarget().name())) {
        if (token != null && !token.isBlank()) {
          template.header("X-Internal-Token", token);
        }
      }
    };
  }
}
