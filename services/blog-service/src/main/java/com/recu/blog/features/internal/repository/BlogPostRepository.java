package com.recu.blog.features.internal.repository;

import com.recu.blog.features.internal.domain.entity.BlogPostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostRepository extends JpaRepository<BlogPostEntity, Long> {}
