package com.recu.blog.client;

import com.recu.blog.client.dto.*;
import com.recu.blog.commons.exception.BusinessException;
import com.recu.blog.commons.exception.ErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Chunk long legacy lists without issuing one HTTP call per post. */
@Component
@RequiredArgsConstructor
public class PersonalizationClient {
  private final UserClient userClient;
  private final AiClient aiClient;
  private static final int BATCH_SIZE = 200;

  public RecommendationProfile profile(Long userId) {
    RecommendationProfile profile = userClient.getRecommendationProfile(userId);
    if (profile == null
        || !userId.equals(profile.userId())
        || profile.profileVersion() == null
        || profile.jobFields() == null
        || profile.wantSkillIds() == null) {
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    }
    return profile;
  }

  public Set<Long> bookmarks(Long userId, List<Long> postIds) {
    return lookup(postIds, batch -> userClient.findBookmarks(userId, new PostIdsRequest(batch)));
  }

  public Set<Long> guides(Long userId, Integer version, List<Long> postIds) {
    return lookup(
        postIds, batch -> aiClient.findGuides(userId, new GuideLookupRequest(version, batch)));
  }

  private Set<Long> lookup(
      List<Long> ids, java.util.function.Function<List<Long>, PostIdsResponse> call) {
    Set<Long> found = new HashSet<>();
    for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
      List<Long> batch = ids.subList(start, Math.min(ids.size(), start + BATCH_SIZE));
      PostIdsResponse response = call.apply(batch);
      if (response == null
          || response.postIds() == null
          || !batch.containsAll(response.postIds())) {
        throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
      }
      found.addAll(response.postIds());
    }
    return found;
  }
}
