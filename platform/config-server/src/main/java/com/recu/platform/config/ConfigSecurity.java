package com.recu.platform.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ConfigSecurity {

  @Bean
  UserDetailsService configUsers(
      @Value("${spring.security.user.name}") String reader,
      @Value("${spring.security.user.password}") String readerPassword,
      @Value("${config.management.username}") String admin,
      @Value("${config.management.password}") String adminPassword) {
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    List<UserDetails> users = new ArrayList<>();
    users.add(
        User.withUsername(reader)
            .password(encoder.encode(readerPassword))
            .roles("CONFIG_READER")
            .build());
    if (admin.isBlank() != adminPassword.isBlank()) {
      throw new IllegalArgumentException(
          "CONFIG_ADMIN_USER and CONFIG_ADMIN_PASSWORD must be set together.");
    }
    if (!admin.isBlank()) {
      if (admin.equals(reader)) {
        throw new IllegalArgumentException("Config admin and reader usernames must be different.");
      }
      users.add(
          User.withUsername(admin)
              .password(encoder.encode(adminPassword))
              .roles("CONFIG_ADMIN", "CONFIG_READER")
              .build());
    }
    return new InMemoryUserDetailsManager(users);
  }

  @Bean
  SecurityFilterChain configSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // HTTP Basic 기반 Bus 관리 POST만 제외하며 다른 경로의 CSRF 정책은 유지한다.
        .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/busrefresh"))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/actuator/busrefresh")
                    .hasRole("CONFIG_ADMIN")
                    .requestMatchers("/actuator/busrefresh/**")
                    .denyAll()
                    .requestMatchers(HttpMethod.GET, "/**")
                    .hasRole("CONFIG_READER")
                    .anyRequest()
                    .denyAll())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
