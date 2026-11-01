package com.recu.blog.features.internal.service;

import com.recu.blog.features.internal.domain.dto.BlogPostSectionResponseDTO;
import com.recu.blog.features.internal.domain.dto.CompanyResponseDTO;
import com.recu.blog.features.internal.domain.dto.PostResponseDTO;
import com.recu.blog.features.internal.domain.dto.TechTagResponseDTO;
import com.recu.blog.features.internal.domain.entity.BlogPostEntity;
import com.recu.blog.features.internal.domain.entity.BlogPostSectionEntity;
import com.recu.blog.features.internal.domain.entity.CompanyEntity;
import com.recu.blog.features.internal.domain.entity.TechTagEntity;
import com.recu.blog.features.internal.repository.BlogPostRepository;
import com.recu.blog.features.internal.repository.BlogPostSectionRepository;
import com.recu.blog.features.internal.repository.CompanyRepository;
import com.recu.blog.features.internal.repository.TechTagRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class BlogInternalService {

    private final BlogPostRepository postRepository;
    private final BlogPostSectionRepository sectionRepository;
    private final CompanyRepository companyRepository;
    private final TechTagRepository techTagRepository;

    public BlogInternalService(
            BlogPostRepository postRepository,
            BlogPostSectionRepository sectionRepository,
            CompanyRepository companyRepository,
            TechTagRepository techTagRepository) {
        this.postRepository = postRepository;
        this.sectionRepository = sectionRepository;
        this.companyRepository = companyRepository;
        this.techTagRepository = techTagRepository;
    }

    public PostResponseDTO getPost(Long postId) {
        BlogPostEntity post = postRepository.findById(postId).orElseThrow(BlogInternalService::notFound);
        return new PostResponseDTO(
                post.getBlog_post_id(),
                post.getCompany().getCompany_id(),
                post.getExternal_id(),
                post.getUrl(),
                post.getTitle(),
                post.getPublished_at(),
                post.getContent_html(),
                post.getContent_text(),
                post.getChar_count(),
                post.getHas_code(),
                post.getCollected_at(),
                post.getIs_tech(),
                post.getField(),
                post.getLevel(),
                post.getSummary());
    }

    public CompanyResponseDTO getCompany(Long companyId) {
        CompanyEntity company = companyRepository.findById(companyId).orElseThrow(BlogInternalService::notFound);
        return new CompanyResponseDTO(
                company.getCompany_id(),
                company.getName(),
                company.getSummary(),
                company.getMain_business(),
                company.getSource_url(),
                company.getChecked_at(),
                company.getFeed_url(),
                company.getFeed_type(),
                company.getLogo_url());
    }

    public List<BlogPostSectionResponseDTO> getSections(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw notFound();
        }
        return sectionRepository.findByPostId(postId).stream()
                .map(BlogInternalService::sectionResponse)
                .toList();
    }

    public List<TechTagResponseDTO> getTechTags(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return techTagRepository.findAllByTechTagIdInOrderByNameAsc(ids).stream()
                .map(tag -> new TechTagResponseDTO(tag.getTechTagId(), tag.getName()))
                .toList();
    }

    private static BlogPostSectionResponseDTO sectionResponse(BlogPostSectionEntity section) {
        return new BlogPostSectionResponseDTO(
                section.getSection_id(),
                section.getSeq(),
                section.getHeading(),
                section.getLevel(),
                section.getContent_text(),
                section.getChar_count());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
