package com.recu.ai.commons.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http, ObjectMapper mapper, @Value("${recu.internal.token:}") String token)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            error(mapper, response, 401, "UNAUTHORIZED", "인증이 필요합니다."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            error(mapper, response, 403, "FORBIDDEN", "접근 권한이 없습니다.")))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/error")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .hasRole("SERVICE")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(
            authenticationFilter(mapper, token), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private OncePerRequestFilter authenticationFilter(ObjectMapper mapper, String token) {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
          String supplied = request.getHeader("X-Internal-Token");
          if (token.isBlank()
              || supplied == null
              || !MessageDigest.isEqual(
                  token.getBytes(StandardCharsets.UTF_8),
                  supplied.getBytes(StandardCharsets.UTF_8))) {
            error(mapper, response, 401, "UNAUTHORIZED", "내부 서비스 인증이 필요합니다.");
            return;
          }
          if (path.equals("/internal/guides/lookup")
              && !isPositiveLong(request.getHeader("userId"))) {
            error(mapper, response, 401, "UNAUTHORIZED", "유효하지 않은 사용자 정보입니다.");
            return;
          }
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new UsernamePasswordAuthenticationToken(
                      "internal-service",
                      null,
                      List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        } else if (path.startsWith("/api/")) {
          String id = request.getHeader("userId");
          if (id != null) {
            if (!isPositiveLong(id)) {
              error(mapper, response, 401, "UNAUTHORIZED", "유효하지 않은 사용자 정보입니다.");
              return;
            }
            long userId = Long.parseLong(id);
            SecurityContextHolder.getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
          }
        }
        chain.doFilter(request, response);
      }
    };
  }

  private boolean isPositiveLong(String value) {
    if (value == null) return false;
    try {
      return Long.parseLong(value) > 0;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private static void error(
      ObjectMapper mapper, HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    var body = mapper.createObjectNode().put("code", code).put("message", message).putNull("field");
    mapper.writeValue(response.getWriter(), body);
  }
}
