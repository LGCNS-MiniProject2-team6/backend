package com.recu.blog.features.internal.ctrl;

import com.recu.blog.features.internal.domain.dto.BlogPostSectionResponseDTO;
import com.recu.blog.features.internal.domain.dto.CompanyResponseDTO;
import com.recu.blog.features.internal.domain.dto.PostResponseDTO;
import com.recu.blog.features.internal.domain.dto.TechTagResponseDTO;
import com.recu.blog.features.internal.service.BlogInternalService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class BlogInternalController {

  private final BlogInternalService blogInternalService;

  public BlogInternalController(BlogInternalService blogInternalService) {
    this.blogInternalService = blogInternalService;
  }

  @GetMapping("/blog/{postId}")
  public PostResponseDTO getPost(@PathVariable Long postId) {
    return blogInternalService.getPost(postId);
  }

  @GetMapping("/blog/{postId}/sections")
  public List<BlogPostSectionResponseDTO> getSections(@PathVariable Long postId) {
    return blogInternalService.getSections(postId);
  }

  @GetMapping("/company/{companyId}")
  public CompanyResponseDTO getCompany(@PathVariable Long companyId) {
    return blogInternalService.getCompany(companyId);
  }

  @GetMapping("/tech-tags")
  public List<TechTagResponseDTO> getTechTags(@RequestParam("ids") List<Long> ids) {
    return blogInternalService.getTechTags(ids);
  }
}
