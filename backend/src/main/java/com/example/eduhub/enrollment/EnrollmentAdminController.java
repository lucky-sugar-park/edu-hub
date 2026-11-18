package com.example.eduhub.enrollment;

import com.example.eduhub.common.CurrentAdmin;
import com.example.eduhub.common.CurrentEmployeeArgumentResolver;
import com.example.eduhub.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회차 명단 조회와 수료 처리(FR-08). 관리자만 쓸 수 있다(D-29).
 *
 * <p>검사 순서는 헤더(400) → 관리자(403) → 본문(400) → 회차(404) → 상태(409)다. 인자는 선언 순서대로 해석되므로
 * {@code @CurrentAdmin}을 본문보다 앞에 둔다. 403 응답의 code는 FORBIDDEN, 409 응답의 code는 SESSION_NOT_STARTED(교육
 * 시작 전), SESSION_NOT_CLOSED(회차 미종료), 503 응답의 code는 LOCK_TIMEOUT이다.
 *
 * <p>{@code @CurrentAdmin} 인자는 {@code @RequestBody}보다 앞에 선언해야 403이 본문 검증 400보다 먼저 나온다(설계 3.2). 인자
 * 순서를 바꾸지 않는다. 컨트롤러에 {@code @Validated}를 붙이거나 인자에 제약 어노테이션을 직접 달지 않는다(검증 시점이 바뀐다).
 */
@Tag(name = "EnrollmentAdmin", description = "회차 명단 조회와 수료 처리(관리자)")
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

  // TODO(NFR-03): 인증 도입 후 로그인한 관리자에서 사번을 꺼내고 헤더를 없앤다(W2)
  @Operation(
      summary = "회차 명단을 조회한다",
      description = "확정·수료자(확정 시각 순)와 대기자(대기 순번 순)를 돌려준다. 취소자는 넣지 않는다")
  @Parameter(
      in = ParameterIn.HEADER,
      name = CurrentEmployeeArgumentResolver.EMPLOYEE_ID_HEADER,
      required = true,
      description = "관리자 사번. 인증 도입 전 임시",
      schema = @Schema(type = "string", maxLength = 20))
  @ApiResponse(responseCode = "200", description = "회차 명단")
  @ApiResponse(
      responseCode = "400",
      description = "사번 헤더 누락·공백·20자 초과, 또는 회차 id가 숫자가 아님",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "관리자가 아님(FORBIDDEN)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/sessions/{sessionId}/roster")
  public SessionRosterResponse getRoster(
      @Parameter(hidden = true) @CurrentAdmin String adminId, @PathVariable Long sessionId) {
    return enrollmentQueryService.getRoster(sessionId);
  }

  // TODO(NFR-03): 인증 도입 후 로그인한 관리자에서 사번을 꺼내고 헤더를 없앤다(W2)
  @Operation(
      summary = "회차의 확정자를 수료 처리한다",
      description =
          "교육이 끝나 종료된 회차에서만 할 수 있다. 일부만 실패해도 성공한 건은 반영하고, 실패한 사번은 failed에 사유와 함께 돌려준다. 대기자는 바꾸지 않는다")
  @Parameter(
      in = ParameterIn.HEADER,
      name = CurrentEmployeeArgumentResolver.EMPLOYEE_ID_HEADER,
      required = true,
      description = "관리자 사번. 인증 도입 전 임시",
      schema = @Schema(type = "string", maxLength = 20))
  @ApiResponse(responseCode = "200", description = "처리 결과(completed, failed). 요청 순서를 따른다")
  @ApiResponse(
      responseCode = "400",
      description = "사번 헤더 오류, 또는 본문 검증 실패(목록 1~200개, 각 사번 공백 불가·20자 이하)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "관리자가 아님(FORBIDDEN)",
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
      @Parameter(hidden = true) @CurrentAdmin String adminId,
      @PathVariable Long sessionId,
      @Valid @RequestBody CompletionRequest request) {
    return completionService.complete(sessionId, request.employeeIds());
  }
}
