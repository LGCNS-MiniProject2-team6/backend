package com.mini8.backend.commons.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mini8.backend.commons.filter.JwtAuthenticationFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** JWT 인증 필터, URL별 접근 정책, CORS를 구성한다. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  public static final String BEARER_AUTH_SCHEME = "bearerAuth";

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;

  @Value("${app.cors-origins}")
  private List<String> corsOrigins;

  public SecurityConfig(
          JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.objectMapper = objectMapper;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .exceptionHandling(
                    exceptions ->
                            exceptions
                                    .authenticationEntryPoint(
                                            (request, response, exception) -> writeUnauthorized(response))
                                    // 로그인은 했지만 권한이 모자란 경우. 이게 없으면 403 이 /error 로 넘어가 401 로 바뀐다
                                    .accessDeniedHandler(
                                            (request, response, exception) -> writeForbidden(response)))
            .authorizeHttpRequests(
                    auth ->
                            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                                    .permitAll()
                                    .requestMatchers("/api/auth/logout")
                                    .authenticated()
                                    .requestMatchers("/api/auth/**")
                                    .permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/tech-tags")
                                    .permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/companies/{id}")
                                    .permitAll()
                                    // 초기 402편 적재용. 관리자 계정을 만드는 길이 아직 없어 잠시 열어 둔다.
                                    // ADMIN 계정이 생기면 이 줄을 지우고 아래 hasRole("ADMIN") 에 맡긴다 (C3)
                                    .requestMatchers(HttpMethod.POST, "/api/admin/collect/import")
                                    .permitAll()
                                    .requestMatchers("/api/admin/**")
                                    .hasRole("ADMIN")
                                    .anyRequest()
                                    .authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  OpenAPI openAPI() {
    SecurityScheme bearerScheme =
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");

    return new OpenAPI()
            .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME, bearerScheme))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(corsOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // 토큰이 응답 헤더로 나가므로 이 둘을 노출해야 브라우저 JS 가 읽는다
    config.setExposedHeaders(List.of("Authorization", "Refresh-Token"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private void writeUnauthorized(HttpServletResponse response) throws IOException {
    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.");
  }

  private void writeForbidden(HttpServletResponse response) throws IOException {
    writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
  }

  private void writeError(HttpServletResponse response, int status, String code, String message)
          throws IOException {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("code", code);
    body.put("message", message);
    body.putNull("field");

    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
