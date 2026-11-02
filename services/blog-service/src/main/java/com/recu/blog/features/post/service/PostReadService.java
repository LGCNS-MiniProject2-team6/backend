package com.recu.blog.features.post.service;

import com.recu.blog.commons.exception.BusinessException;
import com.recu.blog.commons.exception.ErrorCode;
import com.recu.blog.features.internal.domain.entity.BlogPostEntity;
import com.recu.blog.features.internal.domain.entity.BlogPostSectionEntity;
import com.recu.blog.features.internal.repository.BlogPostRepository;
import com.recu.blog.features.internal.repository.BlogPostSectionRepository;
import com.recu.blog.features.post.domain.dto.PostResponseDTO;
import com.recu.blog.features.post.domain.dto.PostResponseDTO.Section;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostReadService {

  private static final Pattern ABSOLUTE_URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

  private final BlogPostRepository postRepository;
  private final BlogPostSectionRepository sectionRepository;

  @Transactional(readOnly = true)
  public PostResponseDTO getPost(Long id) {
    BlogPostEntity post =
        postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<BlogPostSectionEntity> storedSections =
        sectionRepository.findByPostId(id).stream()
            .sorted(Comparator.comparing(BlogPostSectionEntity::getSeq))
            .toList();

    if (storedSections.isEmpty()) {
      return fallbackResponse(post);
    }

    List<Section> sections = new ArrayList<>();
    for (int i = 0; i < storedSections.size(); i++) {
      BlogPostSectionEntity section = storedSections.get(i);
      sections.add(new Section(i + 1, section.getHeading(), section.getChar_count()));
    }
    return response(post, addSectionAnchors(post.getContent_html(), sections.size()), sections);
  }

  private PostResponseDTO fallbackResponse(BlogPostEntity post) {
    Document document = Jsoup.parseBodyFragment(post.getContent_html());
    Element wrapper = document.createElement("div").attr("id", "section-1");
    for (Node node : new ArrayList<>(document.body().childNodes())) {
      wrapper.appendChild(node);
    }
    document.body().appendChild(wrapper);

    Section section = new Section(1, post.getTitle(), post.getChar_count());
    return response(post, document.body().html(), List.of(section));
  }

  private PostResponseDTO response(
      BlogPostEntity post, String contentHtml, List<Section> sections) {
    return new PostResponseDTO(
        post.getBlog_post_id(),
        post.getTitle(),
        post.getPublished_at().toLocalDate(),
        post.getCompany().getName(),
        post.getField() == null || post.getField().isBlank() ? List.of() : List.of(post.getField()),
        postRepository.findSkillNamesByPostId(post.getBlog_post_id()),
        post.getChar_count(),
        post.getUrl(),
        resolveContentUrls(contentHtml, post.getUrl()),
        sections,
        false);
  }

  private String resolveContentUrls(String contentHtml, String baseUrl) {
    Document document = Jsoup.parseBodyFragment(contentHtml);
    document
        .select("img[src], a[href]")
        .forEach(
            element -> {
              String attribute = element.tagName().equals("img") ? "src" : "href";
              element.attr(attribute, resolveUrl(element.attr(attribute), baseUrl));
            });
    document
        .select("img[srcset], source[srcset]")
        .forEach(
            element ->
                element.attr(
                    "srcset",
                    Pattern.compile(",(?=\\s)")
                        .splitAsStream(element.attr("srcset"))
                        .map(String::trim)
                        .map(candidate -> resolveSrcsetCandidate(candidate, baseUrl))
                        .collect(Collectors.joining(", "))));
    return document.body().html();
  }

  private String resolveSrcsetCandidate(String candidate, String baseUrl) {
    int descriptorStart = candidate.indexOf(' ');
    String url = descriptorStart < 0 ? candidate : candidate.substring(0, descriptorStart);
    String descriptor = descriptorStart < 0 ? "" : candidate.substring(descriptorStart);
    return resolveUrl(url, baseUrl) + descriptor;
  }

  private String resolveUrl(String value, String baseUrl) {
    if (value.isBlank() || value.startsWith("#") || ABSOLUTE_URL.matcher(value).find()) {
      return value;
    }
    try {
      return URI.create(baseUrl).resolve(value).toString();
    } catch (IllegalArgumentException ignored) {
      return value;
    }
  }

  private String addSectionAnchors(String contentHtml, int sectionCount) {
    Document document = Jsoup.parseBodyFragment(contentHtml);
    Element body = document.body();
    body.select("[id^=section-]").stream()
        .filter(element -> element.id().matches("section-\\d+"))
        .forEach(element -> element.removeAttr("id"));

    List<Element> headings =
        body.select("h2, h3").stream().filter(heading -> !heading.text().isBlank()).toList();
    for (int seq = 1; seq <= sectionCount; seq++) {
      if (seq <= headings.size()) {
        headings.get(seq - 1).attr("id", "section-" + seq);
      } else {
        body.appendElement("span").attr("id", "section-" + seq);
      }
    }
    return body.html();
  }
}
