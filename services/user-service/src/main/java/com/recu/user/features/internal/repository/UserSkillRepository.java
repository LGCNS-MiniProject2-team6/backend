package com.recu.user.features.internal.repository;

import com.recu.user.features.internal.domain.entity.UserSkillEntity;
import com.recu.user.features.internal.domain.entity.UserSkillId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkillEntity, UserSkillId> {

    @Query(
            "select userSkill from UserSkillEntity userSkill "
                    + "where userSkill.user.userId = :userId "
                    + "order by userSkill.id.techTagId")
    List<UserSkillEntity> findProfileSkills(@Param("userId") Long userId);
}
