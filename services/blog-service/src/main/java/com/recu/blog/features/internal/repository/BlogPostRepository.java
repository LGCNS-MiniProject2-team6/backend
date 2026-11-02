package com.recu.blog.features.internal.repository;

import com.recu.blog.features.internal.domain.entity.BlogPostEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogPostRepository extends JpaRepository<BlogPostEntity, Long> {
  @Query(
      "select tag.techTag.name from BlogPostTagEntity tag where tag.blogPost.blog_post_id = :postId order by coalesce(tag.tag_rank, 2147483647), tag.techTag.name")
  List<String> findSkillNamesByPostId(@Param("postId") Long postId);
}
