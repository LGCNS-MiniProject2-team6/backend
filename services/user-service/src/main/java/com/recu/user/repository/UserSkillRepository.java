package com.recu.user.repository;

import com.recu.user.domain.entity.UserEntity;
import com.recu.user.domain.entity.UserSkillEntity;
import com.recu.user.domain.entity.UserSkillPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkillEntity, UserSkillPk> {

  List<UserSkillEntity> findByUser_UserId(Long userId);

  List<UserSkillEntity> findAllByUserAndSkillType(UserEntity user, String skillType);

  void deleteByUser_UserId(Long userId);

  void deleteAllByUser(UserEntity user);
}
