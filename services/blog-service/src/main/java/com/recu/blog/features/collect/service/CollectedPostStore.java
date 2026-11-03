package com.recu.blog.features.collect.service;

import com.recu.blog.features.collect.domain.CollectPostRequest;
import com.recu.blog.features.collect.event.*;
import com.recu.blog.features.internal.domain.entity.*;
import com.recu.blog.features.internal.repository.*;
import java.time.*;
import java.util.*;
import org.jsoup.nodes.Entities;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CollectedPostStore {
  private final BlogPostRepository posts;
  private final CompanyRepository companies;
  private final TechTagRepository tags;
  private final BlogPostTagRepository postTags;
  private final TransactionTemplate transaction;

  public CollectedPostStore(
      BlogPostRepository posts,
      CompanyRepository companies,
      TechTagRepository tags,
      BlogPostTagRepository postTags,
      PlatformTransactionManager manager) {
    this.posts = posts;
    this.companies = companies;
    this.tags = tags;
    this.postTags = postTags;
    this.transaction = new TransactionTemplate(manager);
  }

  public StoredPost store(CollectPostRequest data) {
    try {
      return transaction.execute(
          status -> {
            var existing = posts.findByUrl(data.url());
            if (existing.isPresent()) return snapshot(existing.get());
            var company =
                companies
                    .findById(data.companyId())
                    .orElseThrow(() -> new InvalidBlogEventException("Unknown companyId"));
            var uniqueTags = new LinkedHashSet<>(data.techTagIds());
            var foundTags = tags.findAllById(uniqueTags);
            if (foundTags.size() != uniqueTags.size())
              throw new InvalidBlogEventException("Unknown techTagId");
            String html =
                data.contentHtml() == null || data.contentHtml().isBlank()
                    ? "<p>" + Entities.escape(data.contentText()) + "</p>"
                    : data.contentHtml();
            var post =
                posts.saveAndFlush(
                    BlogPostEntity.builder()
                        .company(company)
                        .external_id(data.externalPostId())
                        .url(data.url())
                        .title(data.title())
                        .published_at(
                            data.publishedAt()
                                .withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                        .content_html(html)
                        .content_text(data.contentText())
                        .char_count(data.contentText().length())
                        .has_code(html.contains("<code") || html.contains("<pre"))
                        .collected_at(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                        .is_tech(data.isTech())
                        .field(data.field())
                        .level(data.level())
                        .summary(data.summary())
                        .build());
            int rank = 0;
            for (var tag : foundTags) {
              postTags.save(
                  BlogPostTagEntity.builder()
                      .id(new BlogPostTagPk(post.getBlog_post_id(), tag.getTechTagId()))
                      .blogPost(post)
                      .techTag(tag)
                      .tag_rank(++rank)
                      .build());
            }
            return snapshot(post);
          });
    } catch (DataIntegrityViolationException e) {
      // A concurrent URL insert must be read only after the losing transaction rolled back.
      return transaction.execute(
          status -> posts.findByUrl(data.url()).map(this::snapshot).orElseThrow(() -> e));
    }
  }

  private StoredPost snapshot(BlogPostEntity post) {
    var ids = posts.findTechTagIdsByPostId(post.getBlog_post_id());
    return new StoredPost(
        new BlogCreatedEvent.Data(
            post.getBlog_post_id(),
            post.getCompany().getCompany_id(),
            post.getTitle(),
            post.getUrl(),
            ids, post.getField(), post.getCompany().getName()),
        post.getCollected_at().toInstant(ZoneOffset.UTC));
  }

  public record StoredPost(BlogCreatedEvent.Data data, Instant collectedAt) {}
}
