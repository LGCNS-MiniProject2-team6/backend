package com.recu.ai.features.internal.blog.service;

import com.recu.ai.features.internal.blog.domain.dto.CompanyResponseDTO;
import com.recu.ai.features.internal.blog.domain.dto.PostResponseDTO;
import com.recu.ai.features.internal.blog.feign.BlogClient;
public class BlogService {

    private final BlogClient blogClient;

    public BlogService(BlogClient blogClient){
        this.blogClient = blogClient;
    }

    public PostResponseDTO getPost(Long postId){
        return blogClient.getPost(postId);
    }

    public CompanyResponseDTO getCompany(Long companyId){
        return blogClient.getCompany(companyId);
    }
}
