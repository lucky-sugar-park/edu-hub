package com.example.eduhub.enrollment;

import com.example.eduhub.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회차 명단 조회와 수료 처리(FR-08). {@code /api/v1/admin/**}는 보안 필터 체인이 ADMIN 역할을 요구한다(D-30).
 *
 * <p>비관리자는 본문 검증보다 먼저 403이 나온다. 409 응답의 code는 SESSION_NOT_STARTED(교육 시작 전), SESSION_NOT_CLOSED(회차
 * 미종료), 503 응답의 code는 LOCK_TIMEOUT이다. 401(UNAUTHORIZED)·403(FORBIDDEN)은 클래스 수준에 문서화했다.
 */
@Tag(name = "EnrollmentAdmin", description = "회차 명단 조회와 수료 처리(관리자)")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(
    responseCode = "401",
    description = "로그인이 필요함(UNAUTHORIZED)",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
    responseCode = "403",
    description = "관리자가 아님(FORBIDDEN)",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@RestController
@RequestMapping("/api/v1/admin")
public class EnrollmentAdminController {

  private final EnrollmentQueryService enrollmentQueryService;
  private final CompletionService completionService;

  public EnrollmentAdminController(
      EnrollmentQueryService enrollmentQueryService, CompletionService completionService) {
    this.enrollmentQueryService = enrollmentQueryService;
    this.completionService = completionService;
  }

  @Operation(
      summary = "회차 명단을 조회한다",
      description = "확정·수료자(확정 시각 순)와 대기자(대기 순번 순)를 돌려준다. 취소자는 넣지 않는다")
  @ApiResponse(responseCode = "200", description = "회차 명단")
  @ApiResponse(
      responseCode = "400",
      description = "회차 id가 숫자가 아님",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/sessions/{sessionId}/roster")
  public SessionRosterResponse getRoster(@PathVariable Long sessionId) {
    return enrollmentQueryService.getRoster(sessionId);
  }

  @Operation(
      summary = "회차의 확정자를 수료 처리한다",
      description =
          "교육이 끝나 종료된 회차에서만 할 수 있다. 일부만 실패해도 성공한 건은 반영하고, 실패한 사번은 failed에 사유와 함께 돌려준다. 대기자는 바꾸지 않는다")
  @ApiResponse(responseCode = "200", description = "처리 결과(completed, failed). 요청 순서를 따른다")
  @ApiResponse(
      responseCode = "400",
      description = "본문 검증 실패(목록 1~200개, 각 사번 공백 불가·20자 이하)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "교육 시작 전(SESSION_NOT_STARTED), 회차가 아직 종료되지 않음(SESSION_NOT_CLOSED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "회차 락 대기 시간 초과(LOCK_TIMEOUT). 잠시 후 다시 시도할 수 있다",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/sessions/{sessionId}/completions")
  public CompletionResponse complete(
      @PathVariable Long sessionId, @Valid @RequestBody CompletionRequest request) {
    return completionService.complete(sessionId, request.employeeIds());
  }
}
