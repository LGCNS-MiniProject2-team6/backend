package com.recu.blog.database.repository;

import com.recu.blog.database.entity.BlogPostEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogPostRepository extends JpaRepository<BlogPostEntity, Long> {
  Optional<BlogPostEntity> findByUrl(String url);

  boolean existsByUrl(String url);

  @Query(
      """
      SELECT COUNT(p)
      FROM BlogPostEntity p
      WHERE p.company.company_id = :companyId
  """)
  long countByCompanyId(@Param("companyId") Long companyId);

  @Query(
      """
      SELECT MIN(p.published_at)
      FROM BlogPostEntity p
      WHERE p.company.company_id = :companyId
  """)
  LocalDateTime findFirstPublishedAt(@Param("companyId") Long companyId);

  @Query(
      """
      SELECT MAX(p.published_at)
      FROM BlogPostEntity p
      WHERE p.company.company_id = :companyId
  """)
  LocalDateTime findLastPublishedAt(@Param("companyId") Long companyId);
}
