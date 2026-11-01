package com.recu.user.repository;

import com.recu.user.domain.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByGoogleSub(String googleSub);

  Optional<UserEntity> findByRefreshToken(String refreshToken);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE UserEntity u SET u.refreshToken = :refreshToken WHERE u.userId = :userId")
  void updateRefreshToken(@Param("userId") Long userId, @Param("refreshToken") String refreshToken);
}
