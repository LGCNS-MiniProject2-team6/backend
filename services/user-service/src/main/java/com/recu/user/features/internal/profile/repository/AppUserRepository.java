package com.recu.user.features.internal.profile.repository;

import com.recu.user.features.internal.profile.domain.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {}
