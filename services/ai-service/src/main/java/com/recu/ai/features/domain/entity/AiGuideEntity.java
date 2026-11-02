package com.recu.ai.features.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "ai_guide",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ai_guide",
                        columnNames = {"user_id", "blog_post_id", "profile_version", "prompt_version"})
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGuideEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ai_guide_id;

    // app_user와 blog_post는 다른 서비스 소유 테이블이므로 JPA 연관관계 대신 ID만 저장한다.
    @Column(name = "user_id", nullable = false)
    private Long user_id;

    @Column(name = "blog_post_id", nullable = false)
    private Long blog_post_id;

    @Column(nullable = false)
    private Integer profile_version;

    @Column(nullable = false, length = 20)
    private String prompt_version;

    // JSON 배열로 저장한다. 구간이 10개를 넘으면 255자를 넘어가므로 길이 제한 없는 TEXT 로 둔다
    @Column(nullable = true, columnDefinition = "TEXT")
    private String focus_sections;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String section_badges;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String section1_text;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String section2_text;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String section3_text;

    @Column(nullable = false)
    private LocalDateTime created_at;
}
