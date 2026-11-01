package com.recu.blog.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "blog_post_section",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_blog_post_section",
          columnNames = {
            "blog_post_id",
            "seq",
          })
    })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostSectionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long section_id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "blog_post_id", nullable = false)
  private BlogPostEntity blogPost;

  @Column(nullable = false)
  private Integer seq;

  @Column(nullable = false, length = 300)
  private String heading;

  @Column(nullable = true, length = 4)
  private String level;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String content_text;

  @Column(nullable = false)
  private Integer char_count;
}
