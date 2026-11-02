package com.recu.ai.internal;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "recu.internal.blog.enabled", havingValue = "true")
public class BlogSupportRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public BlogSupportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Long> guides(Long userId, Integer version, List<Long> ids) {
    return jdbc.query(
        "select distinct blog_post_id from ai_guide where user_id=:userId and profile_version=:version and blog_post_id in (:ids)",
        Map.of("userId", userId, "version", version, "ids", ids),
        (rs, n) -> rs.getLong(1));
  }
}
