package com.recu.blog.features.internal.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long company_id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = true, length = 200)
    private String summary;

    @Column(nullable = true, length = 300)
    private String main_business;

    @Column(nullable = true, length = 255)
    private String source_url;

    @Column(nullable = true)
    private LocalDateTime checked_at;

    @Column(nullable = false, length = 255)
    private String feed_url;

    @Column(nullable = false, length = 20)
    private String feed_type;

    // 기업 로고 이미지 주소(각 회사 공식 이미지). 없으면 null
    @Column(nullable = true, length = 500)
    private String logo_url;
}
