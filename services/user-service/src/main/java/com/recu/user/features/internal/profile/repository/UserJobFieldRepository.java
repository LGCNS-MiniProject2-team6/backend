package com.recu.user.features.internal.profile.repository;

import com.recu.user.features.internal.profile.domain.entity.UserJobFieldEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJobFieldRepository extends JpaRepository<UserJobFieldEntity, Integer> {

    @Query(
            "select jobField from UserJobFieldEntity jobField "
                    + "where jobField.user.userId = :userId order by jobField.id")
    List<UserJobFieldEntity> findByUserId(@Param("userId") Long userId);
}
