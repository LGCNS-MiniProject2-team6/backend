package com.recu.user.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Narrow read adapter owned by Blog's caller; enable only after the owner DB is configured. */
@Configuration
@ConditionalOnProperty(name = "recu.internal.blog.enabled", havingValue = "true")
public class BlogSupportConfig {
  @Bean
  @ConditionalOnMissingBean(DataSource.class)
  DataSource blogSupportDataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password) {
    return new DriverManagerDataSource(url, username, password);
  }

  @Bean
  @ConditionalOnMissingBean(NamedParameterJdbcTemplate.class)
  NamedParameterJdbcTemplate blogSupportJdbc(DataSource source) {
    return new NamedParameterJdbcTemplate(source);
  }

  @Bean
  FilterRegistrationBean<Filter> blogSupportAuthentication(
      ObjectMapper mapper, @Value("${recu.internal.token:${INTERNAL_API_TOKEN:}}") String token) {
    FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
    bean.setFilter(
        (request, response, chain) -> {
          HttpServletRequest req = (HttpServletRequest) request;
          HttpServletResponse res = (HttpServletResponse) response;
          String supplied = req.getHeader("X-Internal-Token");
          if (token.isBlank()
              || supplied == null
              || !MessageDigest.isEqual(
                  token.getBytes(StandardCharsets.UTF_8),
                  supplied.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            mapper.writeValue(
                res.getWriter(),
                mapper
                    .createObjectNode()
                    .put("code", "UNAUTHORIZED")
                    .put("message", "내부 서비스 인증이 필요합니다.")
                    .putNull("field"));
            return;
          }
          try {
            if (Long.parseLong(req.getHeader("userId")) <= 0) throw new NumberFormatException();
          } catch (NumberFormatException ex) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            mapper.writeValue(
                res.getWriter(),
                mapper
                    .createObjectNode()
                    .put("code", "UNAUTHORIZED")
                    .put("message", "유효하지 않은 사용자 정보입니다.")
                    .put("field", "userId"));
            return;
          }
          chain.doFilter(request, response);
        });
    bean.addUrlPatterns("/internal/*");
    bean.setOrder(-100);
    return bean;
  }
}
