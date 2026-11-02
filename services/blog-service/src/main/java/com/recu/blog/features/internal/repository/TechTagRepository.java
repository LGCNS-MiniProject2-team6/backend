package com.recu.blog.features.internal.repository;

import com.recu.blog.features.internal.domain.entity.TechTagEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechTagRepository extends JpaRepository<TechTagEntity, Long> {

  List<TechTagEntity> findAllByTechTagIdInOrderByNameAsc(List<Long> techTagIds);

  Optional<TechTagEntity> findByName(String name);
}
