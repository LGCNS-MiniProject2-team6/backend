package com.recu.ai.features.ctrl;


import com.recu.ai.features.domain.dto.GuideResponseDTO;
import com.recu.ai.features.service.GuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;

    // generateGuide: 게시글에 대한 AI 가이드 생성
    @Operation(summary = "AI 가이드 생성", description = "게시글에 대한 AI 가이드 생성")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "가이드 생성 성공"),
            @ApiResponse(responseCode = "400", description = "가이드 생성 실패(유효하지 않은 careerYears)"),
            @ApiResponse(responseCode = "401", description = "가이드 생성 실패(토큰 유효성 확인)"),
            @ApiResponse(responseCode = "404", description = "가이드 생성 실패(유효하지 않은 postId)"),
            @ApiResponse(responseCode = "502", description = "가이드 생성 실패(LLM 서비스 호출 실패)")
    })

    // S11
    @PostMapping("/{id}/guide")
    public ResponseEntity<GuideResponseDTO> generateGuide(
            @PathVariable("id") Long id, @RequestHeader Long userId) {
        return ResponseEntity.ok(guideService.generateGuide(userId, id));
    }
}
