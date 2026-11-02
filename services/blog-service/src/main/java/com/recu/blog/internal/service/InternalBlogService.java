package com.recu.blog.internal.service;

import com.recu.blog.commons.exception.*;
import com.recu.blog.features.company.repository.CompanyPostQueryRepository;
import com.recu.blog.features.internal.domain.entity.*;
import com.recu.blog.features.internal.repository.*;
import com.recu.blog.internal.dto.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal reads are Blog-only and never call User or AI, preventing callback cycles. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalBlogService {
  private final BlogPostRepository posts;
  private final BlogPostSectionRepository sections;
  private final CompanyPostQueryRepository queries;
  private final TechTagRepository tags;

  public InternalPostResponse content(Long postId) {
    BlogPostEntity p =
        posts.findById(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    var ordered =
        sections.findByPostId(postId).stream()
            .sorted(Comparator.comparing(BlogPostSectionEntity::getSeq))
            .toList();
    return new InternalPostResponse(
        p.getBlog_post_id(),
        p.getTitle(),
        p.getField(),
        p.getLevel(),
        p.getContent_text(),
        ordered.stream()
            .map(
                s ->
                    new InternalPostResponse.Section(
                        s.getSection_id(),
                        s.getSeq(),
                        s.getHeading(),
                        s.getContent_text(),
                        s.getChar_count()))
            .toList());
  }

  public boolean exists(Long postId) {
    return posts.existsById(postId);
  }

  public PostSummaryResponse summaries(List<Long> ids) {
    Map<Long, List<String>> skills =
        queries.findTags(ids).stream()
            .collect(
                Collectors.groupingBy(
                    t -> t.getId().getBlog_post_id(),
                    LinkedHashMap::new,
                    Collectors.mapping(t -> t.getTechTag().getName(), Collectors.toList())));
    Map<Long, BlogPostEntity> found =
        posts.findAllById(ids).stream()
            .collect(Collectors.toMap(BlogPostEntity::getBlog_post_id, p -> p));
    return new PostSummaryResponse(
        ids.stream()
            .distinct()
            .filter(found::containsKey)
            .map(
                id -> {
                  BlogPostEntity p = found.get(id);
                  return new PostSummaryResponse.Post(
                      id,
                      p.getCompany().getCompany_id(),
                      p.getTitle(),
                      p.getCompany().getName(),
                      p.getSummary(),
                      p.getField() == null || p.getField().isBlank()
                          ? List.of()
                          : List.of(p.getField()),
                      skills.getOrDefault(id, List.of()),
                      p.getPublished_at().toLocalDate(),
                      p.getUrl());
                })
            .toList());
  }

  public Map<String, Object> techTags(List<Long> ids) {
    var found =
        tags.findAllById(ids).stream()
            .map(t -> new Tech(t.getTechTagId(), t.getName(), t.getField()))
            .toList();
    Set<Long> present = found.stream().map(Tech::techTagId).collect(Collectors.toSet());
    return Map.of(
        "techTags",
        found,
        "missingIds",
        ids.stream().distinct().filter(id -> !present.contains(id)).toList());
  }

  public record Tech(Long techTagId, String name, String field) {}
}
