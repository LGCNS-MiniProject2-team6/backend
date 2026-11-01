package com.recu.blog.features.company.service;

import com.recu.blog.client.PersonalizationClient;
import com.recu.blog.client.dto.RecommendationProfile;
import com.recu.blog.commons.exception.*;
import com.recu.blog.database.repository.CompanyRepository;
import com.recu.blog.features.company.domain.dto.*;
import com.recu.blog.features.company.repository.CompanyPostQueryRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Remote calls happen outside the local database read transaction. */
@Service
@RequiredArgsConstructor
public class CompanyService {
  private final CompanyReadService reader;
  private final PersonalizationClient personalization;
  private final CompanyRepository companyRepository;
  private final CompanyPostQueryRepository postQuery;

  public CompanyListResponseDTO getCompanyList(Long userId) {
    RecommendationProfile profile;
    try {
      profile = personalization.profile(userId);
    } catch (BusinessException e) {
      if (e.getErrorCode() == ErrorCode.NOT_FOUND)
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      throw e;
    }
    if (profile.profileVersion() == 0) throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
    return reader.getCompanyList(profile);
  }

  public CompanyPostListResponseDTO getCompanyPostList(
      Long companyId, boolean onlyMySkills, Long userId) {
    if (!companyRepository.existsById(companyId)) throw new BusinessException(ErrorCode.NOT_FOUND);
    List<Long> ids = postQuery.findEligiblePostIds(companyId);
    if (ids.isEmpty())
      return new CompanyPostListResponseDTO(
          new CompanyPostListResponseDTO.Filter(onlyMySkills, 0, 0), List.of());
    RecommendationProfile profile = personalization.profile(userId);
    Set<Long> bookmarks = personalization.bookmarks(userId, ids);
    Set<Long> guides = personalization.guides(userId, profile.profileVersion(), ids);
    return reader.getCompanyPostList(companyId, onlyMySkills, profile, bookmarks, guides);
  }

  public CompanyResponseDTO getCompanyDetail(Long id) {
    return reader.getCompanyDetail(id);
  }
}
