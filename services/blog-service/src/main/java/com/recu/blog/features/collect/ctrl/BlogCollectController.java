package com.recu.blog.features.collect.ctrl;

import com.recu.blog.commons.exception.*;
import com.recu.blog.features.collect.domain.CollectPostRequest;
import com.recu.blog.features.collect.service.BlogEventPublisher;
import com.recu.blog.features.internal.repository.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/collect/posts")
public class BlogCollectController {
  private final BlogEventPublisher publisher;
  private final CompanyRepository companies;
  private final TechTagRepository tags;

  public BlogCollectController(
      BlogEventPublisher publisher, CompanyRepository companies, TechTagRepository tags) {
    this.publisher = publisher;
    this.companies = companies;
    this.tags = tags;
  }

  @PostMapping
  public ResponseEntity<Accepted> collect(@Valid @RequestBody CollectPostRequest request) {
    if (!companies.existsById(request.companyId()))
      throw new BusinessException(ErrorCode.INVALID_INPUT, "companyId");
    var ids = new HashSet<>(request.techTagIds());
    if (tags.findAllById(ids).size() != ids.size())
      throw new BusinessException(ErrorCode.INVALID_INPUT, "techTagIds");
    UUID eventId = publisher.requested(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new Accepted(eventId, "ACCEPTED"));
  }

  public record Accepted(UUID eventId, String status) {}
}
