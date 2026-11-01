package com.recu.blog.database.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blog_post_category")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostCategoryEntity {
  @EmbeddedId private BlogPostCategoryPk id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("blog_post_id")
  @JoinColumn(name = "blog_post_id", nullable = false)
  private BlogPostEntity blogPost;
}
