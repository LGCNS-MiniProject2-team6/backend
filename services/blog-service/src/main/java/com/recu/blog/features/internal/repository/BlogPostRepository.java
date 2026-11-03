package com.recu.blog.features.internal.repository;

import com.recu.blog.features.internal.domain.entity.BlogPostEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogPostRepository extends JpaRepository<BlogPostEntity, Long> {
  Optional<BlogPostEntity> findByUrl(String url);

  @Query(
      "select tag.techTag.techTagId from BlogPostTagEntity tag where tag.blogPost.blog_post_id = :postId order by tag.tag_rank")
  List<Long> findTechTagIdsByPostId(@Param("postId") Long postId);

  @Query(
      "select tag.techTag.name from BlogPostTagEntity tag where tag.blogPost.blog_post_id = :postId order by coalesce(tag.tag_rank, 2147483647), tag.techTag.name")
  List<String> findSkillNamesByPostId(@Param("postId") Long postId);
}
