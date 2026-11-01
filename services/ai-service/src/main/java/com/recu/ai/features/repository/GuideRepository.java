package com.recu.ai.features.repository;

import java.util.Optional;

import com.recu.ai.features.domain.entity.AiGuideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuideRepository extends JpaRepository<AiGuideEntity, Long> {

    @Query(
            "select guide from AiGuideEntity guide "
                    + "where guide.user_id = :userId "
                    + "and guide.blog_post_id = :postId "
                    + "and guide.profile_version = :profileVersion "
                    + "and guide.prompt_version = :promptVersion")
    Optional<AiGuideEntity> findCached(
            @Param("userId") Long userId,
            @Param("postId") Long postId,
            @Param("profileVersion") Integer profileVersion,
            @Param("promptVersion") String promptVersion);
}
