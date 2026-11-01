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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blog_post")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long blog_post_id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private CompanyEntity company;

  @Column(nullable = true, length = 255)
  private String external_id;

  @Column(nullable = false, unique = true, length = 500)
  private String url;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(nullable = false)
  private LocalDateTime published_at;

  @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
  private String content_html;

  @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
  private String content_text;

  @Column(nullable = false)
  private Integer char_count;

  @Column(nullable = false)
  private Boolean has_code;

  @Column(nullable = false)
  private LocalDateTime collected_at;

  @Column(nullable = true)
  private Boolean is_tech;

  @Column(nullable = true, length = 10)
  private String field;

  @Column(nullable = true, length = 4)
  private String level;

  @Column(nullable = true, length = 300)
  private String summary;
}
