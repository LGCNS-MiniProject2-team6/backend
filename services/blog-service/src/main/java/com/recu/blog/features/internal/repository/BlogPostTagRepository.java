package com.recu.blog.features.internal.repository;

import com.recu.blog.features.internal.domain.entity.BlogPostTagEntity;
import com.recu.blog.features.internal.domain.entity.BlogPostTagPk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostTagRepository extends JpaRepository<BlogPostTagEntity, BlogPostTagPk> {}
