package com.recu.blog.features.company.ctrl;

import com.recu.blog.features.company.domain.dto.CompanyListResponseDTO;
import com.recu.blog.features.company.domain.dto.CompanyPostListResponseDTO;
import com.recu.blog.features.company.domain.dto.CompanyResponseDTO;
import com.recu.blog.features.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Company API", description = "기업의 정보와 포스트 목록 관련 API 명세서")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

  private final CompanyService companyService;

  // getCompanyList: 기업 목록 불러오기
  @Operation(summary = "기업 목록 조회", description = "저장된 기업 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "기업 목록 조회 성공"),
    @ApiResponse(responseCode = "401", description = "기업 목록 조회 실패(토큰 유효성 확인)"),
    @ApiResponse(responseCode = "400", description = "기업 목록 조회 실패(프로필 미생성)")
  })
  @GetMapping
  public ResponseEntity<?> getCompanyList(@RequestHeader("userId") Long userId) {
    CompanyListResponseDTO response = companyService.getCompanyList(userId);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  // getCompanyPostList: 글 목록 불러오기
  @Operation(summary = "글 목록 조회", description = "선택한 기업의 글 목록 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "글 목록 조회 성공"),
    @ApiResponse(responseCode = "401", description = "글 목록 조회 실패(토큰 유효성 확인)"),
    @ApiResponse(responseCode = "404", description = "글 목록 조회 실패(유효하지 않은 companyId)")
  })
  @GetMapping("/{id}/posts")
  public ResponseEntity<CompanyPostListResponseDTO> getCompanyPostList(
      @PathVariable("id") Long id,
      @RequestParam(defaultValue = "true") boolean onlyMySkills,
      @RequestHeader("userId") Long userId) {
    return ResponseEntity.ok(companyService.getCompanyPostList(id, onlyMySkills, userId));
  }

  // getCompanyDetail: 기업 상세 정보 조회
  @Operation(summary = "기업 상세 정보 조회", description = "선택한 기업의 상세 정보 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "기업 상세 정보 조회 성공"),
    @ApiResponse(responseCode = "404", description = "기업 상세 정보 조회 실패(유효하지 않은 companyId)")
  })
  @GetMapping("/{id}")
  public ResponseEntity<?> getCompanyDetail(@PathVariable("id") Long id) {
    CompanyResponseDTO result = companyService.getCompanyDetail(id);

    return ResponseEntity.ok(result);
  }
}
