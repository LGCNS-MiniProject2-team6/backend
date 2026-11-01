package com.recu.blog.features.tech.service;

import com.recu.blog.database.entity.TechTagEntity;
import com.recu.blog.database.repository.TechTagRepository;
import com.recu.blog.features.tech.domain.dto.TechResponseDTO;
import com.recu.blog.features.tech.domain.dto.TechResponseDTO.TechTagDTO;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TechService {

  /**
   * 화면에 그리는 순서. 명세의 표 순서를 그대로 옮겼다.
   *
   * <p>번호(techTagId)는 여기에 적지 않는다. 적재 순서대로 붙는 값이라 명세의 예시 번호와 다르고 다시 적재하면 또 바뀐다. 이름은 안 바뀌므로 이름으로 순서만
   * 잡고, 번호는 표에서 읽은 값을 그대로 내려준다. 화면에서도 응답으로 받은 번호를 써야 한다.
   */
  private static final List<String> DISPLAY_ORDER =
      List.of(
          "Spring",
          "Spring Boot",
          "MySQL",
          "Kotlin",
          "SSE",
          "React",
          "JavaScript",
          "Vite",
          "LLM",
          "RAG",
          "MCP",
          "Claude Code",
          "Machine Learning",
          "Airflow",
          "Kafka",
          "Kubernetes",
          "Redis",
          "AWS",
          "Android",
          "Flutter");

  private final TechTagRepository techTagRepository;

  @Transactional(readOnly = true)
  public TechResponseDTO getTechList() {
    List<TechTagDTO> techTags =
        DISPLAY_ORDER.stream()
            .map(this::findOrWarn)
            .filter(Objects::nonNull)
            .map(e -> new TechTagDTO(e.getTech_tag_id(), e.getName(), e.getField()))
            .toList();

    if (techTags.size() != DISPLAY_ORDER.size()) {
      log.warn("기술 칩 {}종 중 {}종만 표에 있습니다.", DISPLAY_ORDER.size(), techTags.size());
    }
    return new TechResponseDTO(techTags);
  }

  private TechTagEntity findOrWarn(String name) {
    return techTagRepository
        .findByName(name)
        .orElseGet(
            () -> {
              log.warn("기술 사전에 없는 이름입니다: {}", name);
              return null;
            });
  }
}
