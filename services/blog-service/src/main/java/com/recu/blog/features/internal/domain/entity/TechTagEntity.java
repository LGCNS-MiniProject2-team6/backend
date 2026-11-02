package com.recu.blog.features.internal.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "tech_tag")
@Getter
public class TechTagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "tech_tag_id")
  private Long techTagId;

  @Column(nullable = false, length = 50)
  private String name;

  // Existing Blog columns needed by technology chips and content ingestion.
  @Column(length = 255)
  private String aliases;

  @Column(length = 30)
  private String field;

  @Column(nullable = false)
  private Integer post_count = 0;

  @Column(nullable = false)
  private Boolean selectable = false;

  protected TechTagEntity() {}
}
