package com.recu.blog.database.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BlogPostCategoryPk implements Serializable {
  // 복합 pk 때문에 Entity에서 분리
  private Long blog_post_id;

  private String name;
}
