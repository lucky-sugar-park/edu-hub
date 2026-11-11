package com.example.eduhub.course;

import com.example.eduhub.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 과정·회차 관리(FR-03). 인증(D-11)을 붙일 때 {@code /api/v1/admin/**}에 관리자 권한을 건다.
 *
 * <p>409 응답의 code: COURSE_CLOSED(종료된 과정), SESSION_CLOSED(종료된 회차), SESSION_STARTED(시작 후 정원·시작 시각
 * 변경), SESSION_NOT_STARTED(시작 전 회차 종료).
 */
@Tag(name = "CourseAdmin", description = "관리자의 과정·회차 등록, 수정, 종료")
@RestController
@RequestMapping("/api/v1/admin")
public class CourseAdminController {

  private final CourseCommandService courseCommandService;

  public CourseAdminController(CourseCommandService courseCommandService) {
    this.courseCommandService = courseCommandService;
  }

  @Operation(summary = "과정을 등록한다")
  @ApiResponse(responseCode = "201", description = "등록 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력 값 오류",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/courses")
  @ResponseStatus(HttpStatus.CREATED)
  public CourseDetailResponse createCourse(@Valid @RequestBody CourseRequest request) {
    return courseCommandService.createCourse(request);
  }

  @Operation(summary = "과정을 수정한다")
  @ApiResponse(responseCode = "200", description = "수정 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력 값 오류",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 과정(COURSE_CLOSED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PutMapping("/courses/{courseId}")
  public CourseDetailResponse updateCourse(
      @PathVariable Long courseId, @Valid @RequestBody CourseRequest request) {
    return courseCommandService.updateCourse(courseId, request);
  }

  @Operation(summary = "과정을 종료한다", description = "모집 종료를 뜻한다. 등록된 회차는 그대로 두고 회차별로 종료한다.")
  @ApiResponse(responseCode = "200", description = "종료 성공")
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "이미 종료된 과정(COURSE_CLOSED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/courses/{courseId}/close")
  public CourseDetailResponse closeCourse(@PathVariable Long courseId) {
    return courseCommandService.closeCourse(courseId);
  }

  @Operation(summary = "과정에 회차를 등록한다")
  @ApiResponse(responseCode = "201", description = "등록 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력 값 오류(시작 시각이 과거인 경우 포함)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 과정(COURSE_CLOSED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/courses/{courseId}/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse createSession(
      @PathVariable Long courseId, @Valid @RequestBody SessionRequest request) {
    return courseCommandService.createSession(courseId, request);
  }

  @Operation(summary = "회차를 수정한다", description = "교육 시작 후에는 장소만 바꿀 수 있다.")
  @ApiResponse(responseCode = "200", description = "수정 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력 값 오류(시작 전 회차의 시작 시각을 과거로 바꾸는 경우 포함)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 회차(SESSION_CLOSED) 또는 시작 후 정원·시작 시각 변경(SESSION_STARTED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PutMapping("/sessions/{sessionId}")
  public SessionResponse updateSession(
      @PathVariable Long sessionId, @Valid @RequestBody SessionRequest request) {
    return courseCommandService.updateSession(sessionId, request);
  }

  @Operation(summary = "회차를 종료한다", description = "교육 완료를 뜻하며 교육 시작 후에만 할 수 있다.")
  @ApiResponse(responseCode = "200", description = "종료 성공")
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "이미 종료된 회차(SESSION_CLOSED) 또는 시작 전 회차(SESSION_NOT_STARTED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/sessions/{sessionId}/close")
  public SessionResponse closeSession(@PathVariable Long sessionId) {
    return courseCommandService.closeSession(sessionId);
  }
}
