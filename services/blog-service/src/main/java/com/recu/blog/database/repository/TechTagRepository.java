package com.recu.blog.database.repository;

import com.recu.blog.database.entity.TechTagEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechTagRepository extends JpaRepository<TechTagEntity, Long> {
  Optional<TechTagEntity> findByName(String name);
}
