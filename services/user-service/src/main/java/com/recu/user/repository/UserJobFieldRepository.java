package com.recu.user.repository;

import com.recu.user.domain.entity.UserEntity;
import com.recu.user.domain.entity.UserJobFieldEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserJobFieldRepository extends JpaRepository<UserJobFieldEntity, Integer> {

  List<UserJobFieldEntity> findByUser_UserId(Long userId);

  @Query(
      "select jobField from UserJobFieldEntity jobField "
          + "where jobField.user.userId = :userId order by jobField.id")
  List<UserJobFieldEntity> findProfileJobFieldsByUserId(@Param("userId") Long userId);

  List<UserJobFieldEntity> findAllByUser(UserEntity user);

  void deleteByUser_UserId(Long userId);

  void deleteAllByUser(UserEntity user);
}
