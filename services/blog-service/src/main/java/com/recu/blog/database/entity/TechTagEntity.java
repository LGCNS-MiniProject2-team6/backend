package com.recu.blog.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_tag")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechTagEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long tech_tag_id;

  @Column(nullable = false, unique = true, length = 50)
  private String name;

  @Column(nullable = true, length = 255)
  private String aliases;

  @Column(nullable = true, length = 30)
  private String field;

  @Builder.Default
  @Column(nullable = false)
  private Integer post_count = 0;

  @Builder.Default
  @Column(nullable = false)
  private Boolean selectable = false;
}
