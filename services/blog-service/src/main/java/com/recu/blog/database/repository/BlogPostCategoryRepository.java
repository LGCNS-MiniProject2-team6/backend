package com.recu.blog.database.repository;

import com.recu.blog.database.entity.BlogPostCategoryEntity;
import com.recu.blog.database.entity.BlogPostCategoryPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogPostCategoryRepository
    extends JpaRepository<BlogPostCategoryEntity, BlogPostCategoryPk> {

  @Query(
      """
        SELECT c
        FROM BlogPostCategoryEntity c
        WHERE c.blogPost.blog_post_id = :postId
    """)
  List<BlogPostCategoryEntity> findCategoriesByPostId(@Param("postId") Long postId);

  @Query(
      """
    SELECT c.id.name, COUNT(c)
    FROM BlogPostCategoryEntity c
    WHERE c.blogPost.blog_post_id IN :postIds
    GROUP BY c.id.name
    ORDER BY COUNT(c) DESC
  """)
  List<Object[]> findTopCategories(@Param("postIds") List<Long> postIds);
}
