package com.recu.blog.features.tech.ctrl;

import com.recu.blog.features.tech.domain.dto.TechResponseDTO;
import com.recu.blog.features.tech.service.TechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tech-tags")
@RequiredArgsConstructor
public class TechController {

  private final TechService techService;

  // getTechList: 기술 칩 목록 조회
  @Operation(summary = "기술 칩 목록 조회", description = "기술 칩 목록을 조회함")
  @ApiResponse(responseCode = "200", description = "기술 칩 목록 불러오기 성공")
  @GetMapping
  public ResponseEntity<TechResponseDTO> getTechList() {
    return ResponseEntity.ok(techService.getTechList());
  }
}
