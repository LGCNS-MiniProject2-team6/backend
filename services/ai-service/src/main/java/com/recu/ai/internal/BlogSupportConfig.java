package com.recu.ai.internal;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
}
