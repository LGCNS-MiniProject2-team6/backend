package com.recu.user.repository;

import com.recu.user.domain.entity.BookmarkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {

  List<BookmarkEntity> findByUser_UserId(Long userId);

  Optional<BookmarkEntity> findByUser_UserIdAndBlogPostId(Long userId, Long blogPostId);

  void deleteByUser_UserIdAndBlogPostId(Long userId, Long blogPostId);

  boolean existsByUser_UserIdAndBlogPostId(Long userId, Long blogPostId);
}
