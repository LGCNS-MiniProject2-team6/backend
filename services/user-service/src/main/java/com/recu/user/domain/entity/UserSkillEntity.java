package com.recu.user.domain.entity;

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
import lombok.Setter;

@Entity
@Table(name = "user_skill")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSkillEntity {

  @EmbeddedId private UserSkillPk id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @Column(name = "skill_type", nullable = false, length = 10)
  private String skillType; // HAVE 또는 WANT
}
