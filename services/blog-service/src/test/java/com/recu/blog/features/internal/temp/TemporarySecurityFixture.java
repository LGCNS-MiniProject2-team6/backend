package com.recu.blog.features.internal.temp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Models the teammate branch's temporary security configuration, which Blog must not scan. */
@Configuration
public class TemporarySecurityFixture {
  @Bean
  SecurityFilterChain temporarySecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(a -> a.anyRequest().denyAll()).build();
  }
}
