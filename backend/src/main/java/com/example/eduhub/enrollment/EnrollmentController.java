package com.example.eduhub.enrollment;

import com.example.eduhub.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수강 신청(FR-04).
 *
 * <p>409 응답의 code: COURSE_CLOSED(종료된 과정), SESSION_CLOSED(종료된 회차), SESSION_STARTED(교육 시작 후 신청),
 * DUPLICATE_ENROLLMENT(같은 회차에 활성 신청이 이미 있음). 503 응답의 code: LOCK_TIMEOUT(회차 락 대기 시간 초과).
 */
@Tag(name = "Enrollment", description = "수강 신청")
@RestController
public class EnrollmentController {

  private final EnrollmentCommandService enrollmentCommandService;
  private final EnrollmentQueryService enrollmentQueryService;

  public EnrollmentController(
      EnrollmentCommandService enrollmentCommandService,
      EnrollmentQueryService enrollmentQueryService) {
    this.enrollmentCommandService = enrollmentCommandService;
    this.enrollmentQueryService = enrollmentQueryService;
  }

  // TODO(NFR-03): 인증 도입 후 로그인 사용자에서 사번을 꺼내고 헤더를 없앤다
  @Operation(
      summary = "회차에 수강 신청한다",
      description =
          "정원 안이고 대기자가 없으면 CONFIRMED, 아니면 WAITING으로 저장한다. WAITING이면 응답의 waitingNumber에 대기 순번이 있고 CONFIRMED면 null이다")
  @ApiResponse(
      responseCode = "201",
      description = "신청 성공(CONFIRMED 또는 WAITING). WAITING이면 waitingNumber 포함")
  @ApiResponse(
      responseCode = "400",
      description = "사번 헤더 누락·공백·20자 초과, 또는 회차 id가 숫자가 아님",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "종료된 과정(COURSE_CLOSED), 종료된 회차(SESSION_CLOSED), 교육 시작 후(SESSION_STARTED),"
              + " 이미 신청한 회차(DUPLICATE_ENROLLMENT)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "회차 락 대기 시간 초과(LOCK_TIMEOUT). 잠시 후 다시 시도할 수 있다",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/sessions/{sessionId}/enrollments")
  @ResponseStatus(HttpStatus.CREATED)
  public EnrollmentResponse enroll(
      @PathVariable Long sessionId,
      @Parameter(description = "신청자 사번. 인증 도입 전 임시")
          @RequestHeader("X-Employee-Id")
          @NotBlank
          @Size(max = 20)
          String employeeId) {
    return enrollmentCommandService.enroll(sessionId, employeeId);
  }

  // TODO(NFR-03): 인증 도입 후 로그인 사용자에서 사번을 꺼내고 헤더를 없앤다
  @Operation(
      summary = "회차의 내 신청과 대기 순번을 조회한다",
      description = "그 회차의 활성 신청(CONFIRMED 또는 WAITING)을 돌려준다. WAITING이면 현재 waitingNumber가 있다")
  @ApiResponse(responseCode = "200", description = "내 활성 신청과 대기 순번")
  @ApiResponse(
      responseCode = "400",
      description = "사번 헤더 누락·공백·20자 초과, 또는 회차 id가 숫자가 아님",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없거나, 그 회차에 내 활성 신청이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/api/v1/sessions/{sessionId}/enrollments/me")
  public EnrollmentResponse getMyEnrollment(
      @PathVariable Long sessionId,
      @Parameter(description = "신청자 사번. 인증 도입 전 임시")
          @RequestHeader("X-Employee-Id")
          @NotBlank
          @Size(max = 20)
          String employeeId) {
    return enrollmentQueryService.getMyEnrollment(sessionId, employeeId);
  }

  // TODO(NFR-03): 인증 도입 후 로그인 사용자에서 사번을 꺼내고 헤더를 없앤다
  @Operation(summary = "회차의 내 신청을 취소한다", description = "교육 시작 전까지만. 확정자가 취소하면 대기 1순위가 확정된다")
  @ApiResponse(responseCode = "200", description = "취소된 신청(status=CANCELLED)")
  @ApiResponse(
      responseCode = "400",
      description = "사번 헤더 누락·공백·20자 초과, 또는 회차 id가 숫자가 아님",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없거나, 그 회차에 내 활성 신청이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "교육 시작 후(SESSION_STARTED), 종료된 회차(SESSION_CLOSED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "회차 락 대기 시간 초과(LOCK_TIMEOUT). 잠시 후 다시 시도할 수 있다",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/sessions/{sessionId}/enrollments/me/cancel")
  public EnrollmentResponse cancel(
      @PathVariable Long sessionId,
      @Parameter(description = "신청자 사번. 인증 도입 전 임시")
          @RequestHeader("X-Employee-Id")
          @NotBlank
          @Size(max = 20)
          String employeeId) {
    return enrollmentCommandService.cancel(sessionId, employeeId);
  }
}
