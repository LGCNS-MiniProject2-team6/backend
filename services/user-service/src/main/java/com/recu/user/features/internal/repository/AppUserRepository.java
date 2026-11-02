package com.recu.user.features.internal.repository;

import com.recu.user.features.internal.domain.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {}
