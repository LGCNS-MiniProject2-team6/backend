package com.recu.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "google_sub", nullable = false, unique = true, length = 100)
  private String googleSub;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "career_years", nullable = true)
  @Min(0)
  private Integer careerYears;

  @Builder.Default
  @Column(name = "profile_version", nullable = false)
  private Integer profileVersion = 0;

  @Column(name = "refresh_token", nullable = true, length = 500)
  private String refreshToken;

  @Builder.Default
  @Column(nullable = false, length = 20)
  private String role = "USER";

  @Column(name = "created_at", nullable = false)
  private LocalDate createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDate updatedAt;

  @PrePersist
  public void prePersist() {
    LocalDate now = LocalDate.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
    if (this.profileVersion == null) {
      this.profileVersion = 0;
    }
    if (this.role == null) {
      this.role = "USER";
    }
  }

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = LocalDate.now();
  }
}
