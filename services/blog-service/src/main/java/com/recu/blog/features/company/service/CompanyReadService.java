package com.recu.blog.features.company.service;

import com.recu.blog.client.dto.RecommendationProfile;
import com.recu.blog.commons.exception.BusinessException;
import com.recu.blog.commons.exception.ErrorCode;
import com.recu.blog.features.company.domain.dto.CompanyListResponseDTO;
import com.recu.blog.features.company.domain.dto.CompanyListResponseDTO.CompanyDTO;
import com.recu.blog.features.company.domain.dto.CompanyListResponseDTO.SkillResponseDTO;
import com.recu.blog.features.company.domain.dto.CompanyPostListResponseDTO;
import com.recu.blog.features.company.domain.dto.CompanyPostListResponseDTO.Filter;
import com.recu.blog.features.company.domain.dto.CompanyPostListResponseDTO.Post;
import com.recu.blog.features.company.domain.dto.CompanyResponseDTO;
import com.recu.blog.features.company.domain.dto.CompanyStatsDTO;
import com.recu.blog.features.company.repository.CompanyPostQueryRepository;
import com.recu.blog.features.internal.domain.entity.BlogPostEntity;
import com.recu.blog.features.internal.domain.entity.BlogPostTagEntity;
import com.recu.blog.features.internal.domain.entity.CompanyEntity;
import com.recu.blog.features.internal.domain.entity.TechTagEntity;
import com.recu.blog.features.internal.repository.BlogPostRepository;
import com.recu.blog.features.internal.repository.BlogPostTagRepository;
import com.recu.blog.features.internal.repository.CompanyRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyReadService {

  private final CompanyRepository companyRepository;
  private final com.recu.blog.features.internal.repository.TechTagRepository techTagRepository;
  private final BlogPostRepository blogPostRepository;
  private final BlogPostTagRepository blogPostTagRepository;
  private final CompanyPostQueryRepository companyPostQueryRepository;

  @Transactional(readOnly = true)
  public CompanyListResponseDTO getCompanyList(RecommendationProfile profile) {
    Set<Long> wantSkillIds = new HashSet<>(profile.wantSkillIds());

    // is_tech == false인 글 제외
    List<BlogPostEntity> techPosts =
        blogPostRepository.findAll().stream()
            .filter(post -> !Boolean.FALSE.equals(post.getIs_tech()))
            .toList();

    Set<Long> techPostIds =
        techPosts.stream().map(BlogPostEntity::getBlog_post_id).collect(Collectors.toSet());

    // 기업별 전체 글 수 계산
    Map<CompanyEntity, Long> postsByCompany =
        techPosts.stream()
            .collect(Collectors.groupingBy(BlogPostEntity::getCompany, Collectors.counting()));

    List<BlogPostTagEntity> blogPostTagEntity = blogPostTagRepository.findAll();

    // 각 기업의 관심 기술별 글 수 계산
    Map<CompanyEntity, Map<TechTagEntity, Integer>> tagsByCompany =
        blogPostTagEntity.stream()
            .filter(tag -> techPostIds.contains(tag.getBlogPost().getBlog_post_id()))
            .filter(tag -> wantSkillIds.contains(tag.getTechTag().getTechTagId()))
            .collect(
                Collectors.groupingBy(
                    tag -> tag.getBlogPost().getCompany(),
                    Collectors.groupingBy(
                        BlogPostTagEntity::getTechTag,
                        Collectors.collectingAndThen(
                            Collectors.mapping(
                                tag -> tag.getBlogPost().getBlog_post_id(), Collectors.toSet()),
                            Set::size))));

    List<CompanyDTO> companies =
        companyRepository.findAll().stream()
            .map(
                company -> {
                  Map<TechTagEntity, Integer> skillCounts =
                      tagsByCompany.getOrDefault(company, Map.of());

                  int matchedPostCount =
                      blogPostTagEntity.stream()
                          .filter(tag -> techPostIds.contains(tag.getBlogPost().getBlog_post_id()))
                          .filter(tag -> tag.getBlogPost().getCompany().equals(company))
                          .filter(tag -> wantSkillIds.contains(tag.getTechTag().getTechTagId()))
                          .map(tag -> tag.getBlogPost().getBlog_post_id())
                          .collect(Collectors.toSet())
                          .size();

                  List<SkillResponseDTO> matchedSkills =
                      skillCounts.entrySet().stream()
                          .map(
                              entry ->
                                  new SkillResponseDTO(entry.getKey().getName(), entry.getValue()))
                          .toList();

                  return new CompanyDTO(
                      company.getCompany_id(),
                      company.getName(),
                      company.getSummary(),
                      company.getLogo_url(),
                      matchedSkills.size(),
                      wantSkillIds.size(),
                      matchedPostCount,
                      postsByCompany.getOrDefault(company, 0L).intValue(),
                      matchedSkills,
                      false);
                })
            .sorted(
                java.util.Comparator.comparingInt(CompanyDTO::matchedSkillCount)
                    .reversed()
                    .thenComparing(
                        java.util.Comparator.comparingInt(CompanyDTO::matchedPostCount).reversed()))
            .collect(Collectors.toCollection(ArrayList::new));

    if (!companies.isEmpty()) {
      CompanyDTO first = companies.get(0);

      companies.set(
          0,
          new CompanyDTO(
              first.companyId(),
              first.name(),
              first.summary(),
              first.logoUrl(),
              first.matchedSkillCount(),
              first.totalSkillCount(),
              first.matchedPostCount(),
              first.totalPostCount(),
              first.matchedSkills(),
              true));
    }

    return CompanyListResponseDTO.builder().companies(companies).build();
  }

  @Transactional(readOnly = true)
  public CompanyPostListResponseDTO getCompanyPostList(
      Long companyId,
      boolean onlyMySkills,
      RecommendationProfile profile,
      Set<Long> bookmarkedPostIds,
      Set<Long> guidedPostIds) {
    if (!companyRepository.existsById(companyId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    List<BlogPostEntity> posts = companyPostQueryRepository.findEligiblePosts(companyId);
    if (posts.isEmpty()) {
      return new CompanyPostListResponseDTO(new Filter(onlyMySkills, 0, 0), List.of());
    }

    List<String> jobFields = profile.jobFields();
    List<String> wantSkills =
        techTagRepository.findAllById(profile.wantSkillIds()).stream()
            .map(TechTagEntity::getName)
            .toList();
    List<Long> postIds = posts.stream().map(BlogPostEntity::getBlog_post_id).toList();

    Map<Long, List<String>> skills = skills(postIds);
    Map<Long, Long> sectionCounts = sectionCounts(postIds);
    List<PostMatch> matches =
        posts.stream()
            .map(
                post -> {
                  List<String> postSkills = skills.getOrDefault(post.getBlog_post_id(), List.of());
                  List<String> matchedSkills = matchedSkills(postSkills, wantSkills);
                  boolean fieldMatches =
                      "General".equals(post.getField())
                          || (post.getField() != null && jobFields.contains(post.getField()));
                  Post response =
                      new Post(
                          post.getBlog_post_id(),
                          post.getTitle(),
                          post.getPublished_at().toLocalDate(),
                          post.getField() == null ? List.of() : List.of(post.getField()),
                          post.getLevel(),
                          post.getSummary(),
                          postSkills,
                          matchedSkills,
                          post.getChar_count(),
                          sectionCounts.getOrDefault(post.getBlog_post_id(), 0L).intValue(),
                          post.getUrl(),
                          bookmarkedPostIds.contains(post.getBlog_post_id()),
                          guidedPostIds.contains(post.getBlog_post_id()));
                  return new PostMatch(response, fieldMatches && !matchedSkills.isEmpty());
                })
            .toList();

    List<Post> personalized =
        matches.stream()
            .filter(PostMatch::personalized)
            .map(PostMatch::post)
            .sorted(
                Comparator.comparingInt((Post post) -> post.matchedSkills().size())
                    .reversed()
                    .thenComparing(Post::publishedAt, Comparator.reverseOrder()))
            .toList();
    List<Post> result =
        onlyMySkills
            ? personalized
            : matches.stream()
                .map(PostMatch::post)
                .sorted(Comparator.comparing(Post::publishedAt).reversed())
                .toList();

    return new CompanyPostListResponseDTO(
        new Filter(onlyMySkills, personalized.size(), posts.size()), result);
  }

  @Transactional(readOnly = true)
  public CompanyResponseDTO getCompanyDetail(Long id) {

    Long companyId = id;

    CompanyEntity company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    // 해당 companty_id 에 post 수 구하는 코드
    List<BlogPostEntity> eligiblePosts = companyPostQueryRepository.findEligiblePosts(companyId);

    long postCount = eligiblePosts.size();
    // 해당 companty_id 에 최신 글 날짜 쿠하는 코드
    LocalDate firstPublishedAt =
        eligiblePosts.isEmpty()
            ? null
            : eligiblePosts.get(eligiblePosts.size() - 1).getPublished_at().toLocalDate();

    // 해당 companty_id 에 오래된 글 날짜 쿠하는 코드
    LocalDate lastPublishedAt =
        eligiblePosts.isEmpty() ? null : eligiblePosts.get(0).getPublished_at().toLocalDate();

    List<Long> postIds = eligiblePosts.stream().map(BlogPostEntity::getBlog_post_id).toList();

    // List<Map<String, Object>> topCategories =
    //     postIds.isEmpty()
    //         ? List.of()
    //         : blogPostCategoryRepository.findTopCategories(postIds).stream()
    //             .limit(5)
    //             .map(
    //                 row -> {
    //                   Map<String, Object> map = new HashMap<>();
    //                   map.put("name", row[0]);
    //                   map.put("count", ((Number) row[1]).intValue());
    //                   return map;
    //                 })
    //             .toList();
    List<Map<String, Object>> topCategories =
        eligiblePosts.stream()
            .map(BlogPostEntity::getField)
            // 필드값이 null 제외
            .filter(Objects::nonNull)
            // 빈 문자열 제외
            .filter(field -> !field.isBlank())
            // 같은 필드끼리 묶김
            .collect(
                Collectors.groupingBy(
                    Function.identity(),
                    // 필드 수 세기
                    Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(
                entry -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("name", entry.getKey());
                  map.put("count", entry.getValue().intValue());
                  return map;
                })
            .toList();
    // 기술 태그 상위 5개 가져오는 코드

    List<Map<String, Object>> topSkills =
        companyPostQueryRepository.findTags(postIds).stream()
            .map(tag -> tag.getTechTag().getName())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(
                entry -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("name", entry.getKey());
                  map.put("count", entry.getValue().intValue());
                  return map;
                })
            .toList();

    CompanyStatsDTO stats =
        CompanyStatsDTO.builder()
            .postCount((int) postCount)
            .firstPublishedAt(
                eligiblePosts.isEmpty()
                    ? null
                    : eligiblePosts.get(eligiblePosts.size() - 1).getPublished_at().toLocalDate())
            .lastPublishedAt(
                eligiblePosts.isEmpty()
                    ? null
                    : eligiblePosts.get(0).getPublished_at().toLocalDate())
            .topCategories(topCategories)
            .topSkills(topSkills)
            .build();

    //     List<Object[]> categoryResults = blogPostCategoryRepository.findTopCategories(id);
    //     for (Object[] row : categoryResults) {
    //    // System.out.println("category = " + row[0] +", count = " + row[1]);
    //     }
    return CompanyResponseDTO.builder()
        .companyId(company.getCompany_id())
        .name(company.getName())
        .logoUrl(company.getLogo_url())
        .summary(company.getSummary())
        .mainBusiness(company.getMain_business())
        .sourceUrl(company.getSource_url())
        .checkedAt(company.getChecked_at() != null ? company.getChecked_at().toLocalDate() : null)
        .stats(stats)
        .build();
  }

  private Map<Long, List<String>> skills(List<Long> postIds) {
    return companyPostQueryRepository.findTags(postIds).stream()
        .collect(
            Collectors.groupingBy(
                tag -> tag.getId().getBlog_post_id(),
                LinkedHashMap::new,
                Collectors.mapping(tag -> tag.getTechTag().getName(), Collectors.toList())));
  }

  private Map<Long, Long> sectionCounts(List<Long> postIds) {
    return companyPostQueryRepository.findSectionCounts(postIds).stream()
        .collect(
            Collectors.toMap(
                CompanyPostQueryRepository.SectionCount::getPostId,
                CompanyPostQueryRepository.SectionCount::getSectionCount));
  }

  private List<String> matchedSkills(List<String> postSkills, List<String> wantSkills) {
    if (wantSkills.isEmpty()) {
      return List.of();
    }
    List<Pattern> patterns =
        wantSkills.stream()
            .map(
                skill ->
                    Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(skill) + "(?![\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
    return postSkills.stream()
        .filter(skill -> patterns.stream().anyMatch(pattern -> pattern.matcher(skill).find()))
        .toList();
  }

  private record PostMatch(Post post, boolean personalized) {}
}
