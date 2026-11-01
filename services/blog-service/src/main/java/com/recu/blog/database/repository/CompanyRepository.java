package com.recu.blog.database.repository;

import com.recu.blog.database.entity.CompanyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
  Optional<CompanyEntity> findByName(String name);
}
