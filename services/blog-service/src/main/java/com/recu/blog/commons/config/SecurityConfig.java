package com.recu.blog.commons.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gateway verifies JWT and overwrites userId. Blog must stay on the private service network. */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain blogSecurity(
      HttpSecurity http, ObjectMapper mapper, @Value("${recu.internal.token:}") String token)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> error(mapper, res, 401, "UNAUTHORIZED", "인증이 필요합니다."))
                    .accessDeniedHandler(
                        (req, res, ex) -> error(mapper, res, 403, "FORBIDDEN", "접근 권한이 없습니다.")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/error")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .hasRole("SERVICE")
                    .requestMatchers(HttpMethod.GET, "/api/tech-tags", "/api/companies/{id}")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                if (req.getRequestURI().startsWith("/internal/")) {
                  String supplied = req.getHeader("X-Internal-Token");
                  if (token.isBlank()
                      || supplied == null
                      || !MessageDigest.isEqual(
                          token.getBytes(StandardCharsets.UTF_8),
                          supplied.getBytes(StandardCharsets.UTF_8))) {
                    error(mapper, res, 401, "UNAUTHORIZED", "내부 서비스 인증이 필요합니다.");
                    return;
                  }
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(
                              "internal-service",
                              null,
                              List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
                } else if (req.getRequestURI().startsWith("/api/")) {
                  String id = req.getHeader("userId");
                  if (id != null) {
                    try {
                      long userId = Long.parseLong(id);
                      if (userId <= 0) throw new NumberFormatException();
                      SecurityContextHolder.getContext()
                          .setAuthentication(
                              new UsernamePasswordAuthenticationToken(
                                  userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                    } catch (NumberFormatException ex) {
                      error(mapper, res, 401, "UNAUTHORIZED", "유효하지 않은 사용자 정보입니다.");
                      return;
                    }
                  }
                }
                chain.doFilter(req, res);
              }
            },
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static void error(
      ObjectMapper mapper, HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    var body = mapper.createObjectNode().put("code", code).put("message", message).putNull("field");
    mapper.writeValue(res.getWriter(), body);
  }
}
