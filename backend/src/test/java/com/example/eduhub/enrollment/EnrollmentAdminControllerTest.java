package com.example.eduhub.enrollment;

import static com.example.eduhub.common.WebMvcSecurityTestConfig.admin;
import static com.example.eduhub.common.WebMvcSecurityTestConfig.employee;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.common.WebMvcSecurityTestConfig;
import com.example.eduhub.course.CourseSessionStatus;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(EnrollmentAdminController.class)
@Import(WebMvcSecurityTestConfig.class)
class EnrollmentAdminControllerTest {

  private static final String ROSTER_URL = "/api/v1/admin/sessions/3/roster";
  private static final String COMPLETIONS_URL = "/api/v1/admin/sessions/3/completions";
  private static final RequestPostProcessor ADMIN = admin("TEST-ADMIN");
  private static final RequestPostProcessor EMPLOYEE = employee("E1001");
  private static final Instant T0 = Instant.parse("2026-11-12T01:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EnrollmentQueryService enrollmentQueryService;
  @MockitoBean private CompletionService completionService;

  /** auth가 null이면 토큰 없이 보낸다. */
  private MockHttpServletRequestBuilder complete(RequestPostProcessor auth, String body) {
    MockHttpServletRequestBuilder builder =
        post(COMPLETIONS_URL).contentType(MediaType.APPLICATION_JSON);
    if (auth != null) {
      builder.with(auth);
    }
    return body == null ? builder : builder.content(body);
  }

  private static String idsJson(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> "\"E" + i + "\"")
        .collect(Collectors.joining(",", "{\"employeeIds\":[", "]}"));
  }

  @Test
  void rosterReturnsConfirmedAndWaiting() throws Exception {
    given(enrollmentQueryService.getRoster(3L))
        .willReturn(
            new SessionRosterResponse(
                3L,
                5L,
                "JPA 입문",
                T0,
                "본사 3층",
                10,
                CourseSessionStatus.CLOSED,
                List.of(
                    new SessionRosterResponse.ConfirmedEntry(
                        1L, "E1", EnrollmentStatus.COMPLETED, T0, T0.plusSeconds(60)),
                    new SessionRosterResponse.ConfirmedEntry(
                        2L, "E2", EnrollmentStatus.CONFIRMED, T0, null)),
                List.of(new SessionRosterResponse.WaitingEntry(5L, "W1", 1, T0))));

    mockMvc
        .perform(get(ROSTER_URL).with(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(3))
        .andExpect(jsonPath("$.courseId").value(5))
        .andExpect(jsonPath("$.courseTitle").value("JPA 입문"))
        .andExpect(jsonPath("$.startAt").value("2026-11-12T01:00:00Z"))
        .andExpect(jsonPath("$.place").value("본사 3층"))
        .andExpect(jsonPath("$.capacity").value(10))
        .andExpect(jsonPath("$.sessionStatus").value("CLOSED"))
        .andExpect(jsonPath("$.confirmed[0].enrollmentId").value(1))
        .andExpect(jsonPath("$.confirmed[0].employeeId").value("E1"))
        .andExpect(jsonPath("$.confirmed[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.confirmed[0].completedAt").exists())
        .andExpect(jsonPath("$.confirmed[1].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.confirmed[1].completedAt").doesNotExist())
        .andExpect(jsonPath("$.waiting[0].employeeId").value("W1"))
        .andExpect(jsonPath("$.waiting[0].waitingNumber").value(1))
        .andExpect(jsonPath("$.waiting[0].appliedAt").exists());
  }

  @Test
  void rosterRejectsNonAdminWith403WithoutCallingService() throws Exception {
    mockMvc
        .perform(get(ROSTER_URL).with(EMPLOYEE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(enrollmentQueryService);
  }

  @Test
  void rosterRejectsMissingTokenWith401() throws Exception {
    mockMvc
        .perform(get(ROSTER_URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(enrollmentQueryService);
  }

  @Test
  void rosterReturns404WhenSessionMissing() throws Exception {
    given(enrollmentQueryService.getRoster(3L)).willThrow(new SessionNotFoundException(3L));

    mockMvc
        .perform(get(ROSTER_URL).with(ADMIN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void completeReturnsCompletedAndFailedWithReasons() throws Exception {
    given(completionService.complete(3L, List.of("E1", "E2", "E3")))
        .willReturn(
            new CompletionResponse(
                List.of("E1"),
                List.of(
                    new CompletionResponse.Failure("E2", CompletionFailureReason.NOT_CONFIRMED),
                    new CompletionResponse.Failure("E3", CompletionFailureReason.NOT_ENROLLED))));

    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"E1\",\"E2\",\"E3\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed[0]").value("E1"))
        .andExpect(jsonPath("$.failed[0].employeeId").value("E2"))
        .andExpect(jsonPath("$.failed[0].reason").value("NOT_CONFIRMED"))
        .andExpect(jsonPath("$.failed[1].reason").value("NOT_ENROLLED"));
  }

  @Test
  void completeRejectsNonAdminWith403WithoutCallingService() throws Exception {
    mockMvc
        .perform(complete(EMPLOYEE, "{\"employeeIds\":[\"E1\"]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(completionService);
  }

  @Test
  void completeRejectsMissingTokenWith401() throws Exception {
    mockMvc
        .perform(complete(null, "{\"employeeIds\":[\"E1\"]}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(completionService);
  }

  @Test
  void adminRoleIsCheckedBeforeBodyValidation() throws Exception {
    // 비관리자가 잘못된 본문을 보내도 400이 아니라 403이다(보안 필터가 컨트롤러보다 먼저 막는다).
    mockMvc
        .perform(complete(EMPLOYEE, "{\"employeeIds\":[]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(complete(EMPLOYEE, null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(completionService);
  }

  @Test
  void missingTokenWithInvalidBodyIsStill401() throws Exception {
    mockMvc
        .perform(complete(null, "{\"employeeIds\":[]}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(completionService);
  }

  @Test
  void completeRejectsInvalidBodiesWith400WithoutCallingService() throws Exception {
    String[] bodies = {
      "{\"employeeIds\":[]}",
      "{}",
      "{\"employeeIds\":null}",
      idsJson(201),
      "{\"employeeIds\":[\"" + "A".repeat(21) + "\"]}",
      "{\"employeeIds\":[\" \"]}",
      "{\"employeeIds\":[\"\"]}",
      "{\"employeeIds\":[\"E1\",null]}"
    };
    for (String body : bodies) {
      mockMvc
          .perform(complete(ADMIN, body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
    mockMvc.perform(complete(ADMIN, null)).andExpect(status().isBadRequest());

    verifyNoInteractions(completionService);
  }

  @Test
  void emptyListReportsSingleMessage() throws Exception {
    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("employeeIds: 1~200개여야 합니다"));
  }

  @Test
  void tooLongElementReportsIndexedFieldAndMessage() throws Exception {
    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"" + "A".repeat(21) + "\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("employeeIds[0]: 1~20자여야 합니다"));
  }

  @Test
  void completeAcceptsBoundarySizes() throws Exception {
    given(completionService.complete(anyLong(), any()))
        .willReturn(new CompletionResponse(List.of(), List.of()));

    mockMvc.perform(complete(ADMIN, idsJson(200))).andExpect(status().isOk());
    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"" + "A".repeat(20) + "\"]}"))
        .andExpect(status().isOk());
    verify(completionService).complete(3L, List.of("A".repeat(20)));
  }

  @Test
  void completeReturns404WhenSessionMissing() throws Exception {
    given(completionService.complete(anyLong(), any())).willThrow(new SessionNotFoundException(3L));

    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"E1\"]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void completeReturns409WhenSessionNotStarted() throws Exception {
    given(completionService.complete(anyLong(), any()))
        .willThrow(new ConflictException("SESSION_NOT_STARTED", "시작 전"));

    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"E1\"]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_STARTED"));
  }

  @Test
  void completeReturns409WhenSessionNotClosed() throws Exception {
    given(completionService.complete(anyLong(), any()))
        .willThrow(new ConflictException("SESSION_NOT_CLOSED", "미종료"));

    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"E1\"]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_CLOSED"));
  }

  @Test
  void completeReturns503OnLockTimeout() throws Exception {
    given(completionService.complete(anyLong(), any()))
        .willThrow(new CannotAcquireLockException("timeout"));

    mockMvc
        .perform(complete(ADMIN, "{\"employeeIds\":[\"E1\"]}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
  }
}
