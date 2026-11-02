package com.recu.blog.features.post.ctrl;

import com.recu.blog.features.post.domain.dto.PostResponseDTO;
import com.recu.blog.features.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  // getPost: 게시글 원본 조회
  @Operation(summary = "게시글 원본 조회", description = "게시글의 원본과 목차를 조회함")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "게시글 조회 성공"),
    @ApiResponse(responseCode = "401", description = "게시글 조회 실패(토큰 유효성 확인)"),
    @ApiResponse(responseCode = "404", description = "게시글 조회 실패(유효하지 않은 postId)")
  })

  // 게시글 상세 조회
  @GetMapping("/{id}")
  public ResponseEntity<PostResponseDTO> getPost(
      @PathVariable("id") Long id, @RequestHeader("userId") Long userId) {
    return ResponseEntity.ok(postService.getPost(id, userId));
  }
}
