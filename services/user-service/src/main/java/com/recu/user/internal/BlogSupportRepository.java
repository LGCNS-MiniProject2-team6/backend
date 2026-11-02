package com.recu.user.internal;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
@ConditionalOnProperty(name = "recu.internal.blog.enabled", havingValue = "true")
public class BlogSupportRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public BlogSupportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RecommendationProfile profile(Long userId) {
    Map<String, Object> args = Map.of("userId", userId);
    List<Integer> versions =
        jdbc.query(
            "select profile_version from app_user where user_id=:userId",
            args,
            (rs, n) -> rs.getInt(1));
    if (versions.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    List<String> fields =
        jdbc.query(
            "select job_field from user_job_field where user_id=:userId order by id",
            args,
            (rs, n) -> rs.getString(1));
    List<Long> skills =
        jdbc.query(
            "select tech_tag_id from user_skill where user_id=:userId and skill_type='WANT' order by tech_tag_id",
            args,
            (rs, n) -> rs.getLong(1));
    return new RecommendationProfile(userId, versions.get(0), fields, skills);
  }

  public List<Long> bookmarks(Long userId, List<Long> ids) {
    return jdbc.query(
        "select distinct blog_post_id from bookmark where user_id=:userId and blog_post_id in (:ids)",
        Map.of("userId", userId, "ids", ids),
        (rs, n) -> rs.getLong(1));
  }

  public record RecommendationProfile(
      Long userId, Integer profileVersion, List<String> jobFields, List<Long> wantSkillIds) {}
}
