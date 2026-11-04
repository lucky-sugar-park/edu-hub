package com.example.eduhub.course;

import com.example.eduhub.common.ErrorResponse;
import com.example.eduhub.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "교육과정 조회")
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

  private final CourseQueryService courseQueryService;

  public CourseController(CourseQueryService courseQueryService) {
    this.courseQueryService = courseQueryService;
  }

  @Operation(summary = "과정 목록을 제목과 분류로 검색한다")
  @Parameter(
      name = "page",
      in = ParameterIn.QUERY,
      description = "페이지 번호 (0부터 시작)",
      schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
  @Parameter(
      name = "size",
      in = ParameterIn.QUERY,
      description = "페이지 크기",
      schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
  @GetMapping
  public PageResponse<CourseSummaryResponse> searchCourses(
      @Parameter(description = "제목 부분 일치") @RequestParam(required = false) String keyword,
      @Parameter(description = "분류 정확히 일치") @RequestParam(required = false) String category,
      @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
    return courseQueryService.searchCourses(keyword, category, pageable);
  }

  @Operation(summary = "과정 상세와 회차 목록을 조회한다")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/{id}")
  public CourseDetailResponse getCourse(@PathVariable Long id) {
    return courseQueryService.getCourse(id);
  }
}
