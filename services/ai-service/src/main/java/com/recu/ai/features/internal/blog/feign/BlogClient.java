package com.recu.ai.features.internal.blog.feign;


import com.recu.ai.features.internal.blog.domain.dto.CompanyResponseDTO;
import com.recu.ai.features.internal.blog.domain.dto.BlogPostSectionResponseDTO;
import com.recu.ai.features.internal.blog.domain.dto.PostResponseDTO;
import com.recu.ai.features.internal.blog.domain.dto.TechTagResponseDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "blog-service")
public interface BlogClient {

    @GetMapping("/internal/blog/{postId}")
    PostResponseDTO getPost(@PathVariable("postId") Long postId);

    @GetMapping("/internal/company/{companyId}")
    CompanyResponseDTO getCompany(@PathVariable("companyId") Long companyId);

    @GetMapping("/internal/blog/{postId}/sections")
    List<BlogPostSectionResponseDTO> getSections(@PathVariable("postId") Long postId);

    @GetMapping("/internal/tech-tags")
    List<TechTagResponseDTO> getTechTags(@RequestParam("ids") List<Long> ids);
}
