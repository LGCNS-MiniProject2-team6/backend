package com.recu.blog.features.post.service;

import com.recu.blog.client.PersonalizationClient;
import com.recu.blog.features.post.domain.dto.PostResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {
  private final PostReadService reader;
  private final PersonalizationClient personalization;

  public PostResponseDTO getPost(Long id, Long userId) {
    PostResponseDTO post = reader.getPost(id);
    boolean bookmarked = personalization.bookmarks(userId, List.of(id)).contains(id);
    return new PostResponseDTO(
        post.postId(),
        post.title(),
        post.publishedAt(),
        post.companyName(),
        post.categories(),
        post.skills(),
        post.charCount(),
        post.originalUrl(),
        post.contentHtml(),
        post.sections(),
        bookmarked);
  }
}
