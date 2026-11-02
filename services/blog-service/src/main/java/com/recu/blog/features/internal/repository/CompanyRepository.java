package com.recu.blog.features.internal.repository;

import com.recu.blog.features.internal.domain.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {}
