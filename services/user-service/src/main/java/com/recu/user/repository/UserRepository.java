package com.recu.user.repository;

import com.recu.user.domain.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByGoogleSub(String googleSub);

  Optional<UserEntity> findByRefreshToken(String refreshToken);
}
