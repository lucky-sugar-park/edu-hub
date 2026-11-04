package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.common.PageResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseController.class)
@ActiveProfiles("test")
class CourseControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CourseQueryService courseQueryService;

  @Test
  void searchCoursesReturnsPageResponse() throws Exception {
    CourseSummaryResponse course = new CourseSummaryResponse(1L, "JPA 입문", "개발", CourseStatus.OPEN);
    given(courseQueryService.searchCourses(isNull(), isNull(), any(Pageable.class)))
        .willReturn(new PageResponse<>(List.of(course), 0, 20, 1));

    mockMvc
        .perform(get("/api/v1/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1))
        .andExpect(jsonPath("$.content[0].title").value("JPA 입문"))
        .andExpect(jsonPath("$.content[0].category").value("개발"))
        .andExpect(jsonPath("$.content[0].status").value("OPEN"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void searchCoursesPassesConditionsToService() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/courses")
                .param("keyword", "JPA")
                .param("category", "개발")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk());

    Pageable pageable = capturePageable("JPA", "개발");
    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(5);
  }

  @Test
  void searchCoursesUsesDefaultPage() throws Exception {
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isOk());

    Pageable pageable = capturePageable(null, null);
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
  }

  @Test
  void searchCoursesLimitsPageSize() throws Exception {
    mockMvc.perform(get("/api/v1/courses").param("size", "1000")).andExpect(status().isOk());

    assertThat(capturePageable(null, null).getPageSize()).isEqualTo(100);
  }

  @Test
  void getCourseReturnsSessionsWithRemainingSeats() throws Exception {
    SessionResponse session =
        new SessionResponse(
            10L, Instant.parse("2026-12-01T01:00:00Z"), "본사 3층", 20, 20, CourseSessionStatus.OPEN);
    given(courseQueryService.getCourse(1L))
        .willReturn(
            new CourseDetailResponse(
                1L, "JPA 입문", "개발", "JPA 기초 과정", CourseStatus.OPEN, List.of(session)));

    mockMvc
        .perform(get("/api/v1/courses/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.description").value("JPA 기초 과정"))
        .andExpect(jsonPath("$.sessions[0].id").value(10))
        .andExpect(jsonPath("$.sessions[0].startAt").value("2026-12-01T01:00:00Z"))
        .andExpect(jsonPath("$.sessions[0].place").value("본사 3층"))
        .andExpect(jsonPath("$.sessions[0].capacity").value(20))
        .andExpect(jsonPath("$.sessions[0].remainingSeats").value(20))
        .andExpect(jsonPath("$.sessions[0].status").value("OPEN"));
  }

  @Test
  void getCourseReturnsNotFoundForUnknownId() throws Exception {
    given(courseQueryService.getCourse(eq(999L))).willThrow(new CourseNotFoundException(999L));

    mockMvc
        .perform(get("/api/v1/courses/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("과정을 찾을 수 없습니다: 999"));
  }

  @Test
  void getCourseReturnsBadRequestForNonNumericId() throws Exception {
    mockMvc
        .perform(get("/api/v1/courses/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("Bad Request"));
  }

  private Pageable capturePageable(String keyword, String category) {
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(courseQueryService).searchCourses(eq(keyword), eq(category), captor.capture());
    return captor.getValue();
  }
}
