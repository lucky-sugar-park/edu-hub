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

  public EnrollmentController(EnrollmentCommandService enrollmentCommandService) {
    this.enrollmentCommandService = enrollmentCommandService;
  }

  // TODO(NFR-03): 인증 도입 후 로그인 사용자에서 사번을 꺼내고 헤더를 없앤다
  @Operation(summary = "회차에 수강 신청한다", description = "정원 안이면 CONFIRMED, 정원이 찼으면 WAITING으로 저장한다")
  @ApiResponse(responseCode = "201", description = "신청 성공(CONFIRMED 또는 WAITING)")
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
}
