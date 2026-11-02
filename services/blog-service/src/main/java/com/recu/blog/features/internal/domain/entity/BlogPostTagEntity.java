package com.recu.blog.features.internal.domain.entity;

import jakarta.persistence.Column;
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
@Table(name = "blog_post_tag")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostTagEntity {
  @EmbeddedId private BlogPostTagPk id;

  // fk관계 복합pk시 Maps사용
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("blog_post_id")
  @JoinColumn(name = "blog_post_id", nullable = false)
  private BlogPostEntity blogPost;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("tech_tag_id")
  @JoinColumn(name = "tech_tag_id", nullable = false)
  private TechTagEntity techTag;

  @Column(nullable = true)
  private Integer tag_rank;
}
