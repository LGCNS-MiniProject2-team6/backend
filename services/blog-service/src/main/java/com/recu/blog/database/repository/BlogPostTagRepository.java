package com.recu.blog.database.repository;

import com.recu.blog.database.entity.BlogPostTagEntity;
import com.recu.blog.database.entity.BlogPostTagPk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostTagRepository extends JpaRepository<BlogPostTagEntity, BlogPostTagPk> {}
