package com.recu.user.repository;

import com.recu.user.domain.entity.UserJobFieldEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserJobFieldRepository extends JpaRepository<UserJobFieldEntity, Integer> {

  List<UserJobFieldEntity> findByUser_UserId(Long userId);

  void deleteByUser_UserId(Long userId);
}
