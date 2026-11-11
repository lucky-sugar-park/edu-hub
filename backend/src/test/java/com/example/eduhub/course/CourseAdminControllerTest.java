package com.example.eduhub.course;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.common.InvalidRequestException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseAdminController.class)
@ActiveProfiles("test")
class CourseAdminControllerTest {

  private static final Instant START = Instant.parse("2026-12-01T01:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CourseCommandService courseCommandService;

  @Test
  void createCourseReturnsCreated() throws Exception {
    given(courseCommandService.createCourse(new CourseRequest("JPA 입문", "개발", "기초")))
        .willReturn(
            new CourseDetailResponse(1L, "JPA 입문", "개발", "기초", CourseStatus.OPEN, List.of()));

    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"JPA 입문","category":"개발","description":"기초"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.sessions").isEmpty());
  }

  @Test
  void createCourseRejectsBlankAndTooLongFields() throws Exception {
    String longTitle = "가".repeat(201);

    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + longTitle + "\",\"category\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("category: 필수 값입니다, title: 200자 이하여야 합니다"));
    verifyNoInteractions(courseCommandService);
  }

  @Test
  void createCourseRejectsMalformedJson() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/courses").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void updateCourseReturnsConflictWhenClosed() throws Exception {
    given(courseCommandService.updateCourse(eq(1L), any()))
        .willThrow(new ConflictException("COURSE_CLOSED", "종료된 과정입니다: 1"));

    mockMvc
        .perform(
            put("/api/v1/admin/courses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"category\":\"c\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COURSE_CLOSED"))
        .andExpect(jsonPath("$.message").value("종료된 과정입니다: 1"));
  }

  @Test
  void updateCourseReturnsNotFound() throws Exception {
    given(courseCommandService.updateCourse(eq(9L), any()))
        .willThrow(new CourseNotFoundException(9L));

    mockMvc
        .perform(
            put("/api/v1/admin/courses/9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"category\":\"c\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void closeCourseReturnsClosedCourse() throws Exception {
    given(courseCommandService.closeCourse(1L))
        .willReturn(
            new CourseDetailResponse(1L, "JPA 입문", "개발", null, CourseStatus.CLOSED, List.of()));

    mockMvc
        .perform(post("/api/v1/admin/courses/1/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  @Test
  void createSessionReturnsCreated() throws Exception {
    given(courseCommandService.createSession(1L, new SessionRequest(START, "본사 3층", 20)))
        .willReturn(new SessionResponse(10L, START, "본사 3층", 20, 20, CourseSessionStatus.OPEN));

    mockMvc
        .perform(
            post("/api/v1/admin/courses/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startAt":"2026-12-01T01:00:00Z","place":"본사 3층","capacity":20}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.startAt").value("2026-12-01T01:00:00Z"));
  }

  @Test
  void createSessionRejectsInvalidFields() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/courses/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"place\":\"\",\"capacity\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("capacity: 1 이상이어야 합니다, place: 필수 값입니다, startAt: 필수 값입니다"));
    verifyNoInteractions(courseCommandService);
  }

  @Test
  void createSessionRejectsTooLargeCapacity() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/courses/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"startAt\":\"2026-12-01T01:00:00Z\",\"place\":\"본사\",\"capacity\":1001}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("capacity: 1000 이하여야 합니다"));
  }

  @Test
  void createSessionReturnsBadRequestForPastStart() throws Exception {
    given(courseCommandService.createSession(eq(1L), any()))
        .willThrow(new InvalidRequestException("startAt: 현재 이후 시각이어야 합니다"));

    mockMvc
        .perform(
            post("/api/v1/admin/courses/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startAt\":\"2020-01-01T00:00:00Z\",\"place\":\"본사\",\"capacity\":10}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("startAt: 현재 이후 시각이어야 합니다"));
  }

  @Test
  void updateSessionReturnsConflictAfterStart() throws Exception {
    given(courseCommandService.updateSession(eq(10L), any()))
        .willThrow(
            new ConflictException("SESSION_STARTED", "교육이 시작된 회차는 정원과 시작 시각을 바꿀 수 없습니다: 10"));

    mockMvc
        .perform(
            put("/api/v1/admin/sessions/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startAt\":\"2026-12-01T01:00:00Z\",\"place\":\"본사\",\"capacity\":30}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STARTED"));
    verify(courseCommandService).updateSession(10L, new SessionRequest(START, "본사", 30));
  }

  @Test
  void closeSessionReturnsConflictBeforeStart() throws Exception {
    given(courseCommandService.closeSession(10L))
        .willThrow(new ConflictException("SESSION_NOT_STARTED", "아직 시작하지 않은 회차는 종료할 수 없습니다: 10"));

    mockMvc
        .perform(post("/api/v1/admin/sessions/10/close"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_STARTED"));
  }

  @Test
  void closeSessionReturnsNotFound() throws Exception {
    given(courseCommandService.closeSession(99L)).willThrow(new SessionNotFoundException(99L));

    mockMvc
        .perform(post("/api/v1/admin/sessions/99/close"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("회차를 찾을 수 없습니다: 99"));
  }
}
