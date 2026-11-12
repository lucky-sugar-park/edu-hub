package com.example.eduhub.enrollment;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@ActiveProfiles("test")
class EnrollmentControllerTest {

  private static final String URL = "/api/v1/sessions/3/enrollments";
  private static final String ME_URL = "/api/v1/sessions/3/enrollments/me";
  private static final String CANCEL_URL = "/api/v1/sessions/3/enrollments/me/cancel";
  private static final String HEADER = "X-Employee-Id";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EnrollmentCommandService enrollmentCommandService;
  @MockitoBean private EnrollmentQueryService enrollmentQueryService;

  @Test
  void enrollReturnsCreated() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willReturn(
            new EnrollmentResponse(
                10L,
                3L,
                "E1001",
                EnrollmentStatus.CONFIRMED,
                null,
                Instant.parse("2026-11-12T01:00:00Z")));

    mockMvc
        .perform(post(URL).header(HEADER, "E1001"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.sessionId").value(3))
        .andExpect(jsonPath("$.employeeId").value("E1001"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.appliedAt").value("2026-11-12T01:00:00Z"));
    verify(enrollmentCommandService).enroll(3L, "E1001");
  }

  @Test
  void enrollWaitingResponseHasWaitingNumber() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1002"))
        .willReturn(
            new EnrollmentResponse(
                11L,
                3L,
                "E1002",
                EnrollmentStatus.WAITING,
                4,
                Instant.parse("2026-11-12T01:00:00Z")));

    mockMvc
        .perform(post(URL).header(HEADER, "E1002"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("WAITING"))
        .andExpect(jsonPath("$.waitingNumber").value(4))
        .andExpect(jsonPath("$.waitingNumber").isNumber());
  }

  @Test
  void enrollConfirmedResponseHasNullWaitingNumber() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willReturn(
            new EnrollmentResponse(
                10L,
                3L,
                "E1001",
                EnrollmentStatus.CONFIRMED,
                null,
                Instant.parse("2026-11-12T01:00:00Z")));

    mockMvc
        .perform(post(URL).header(HEADER, "E1001"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.waitingNumber").value(nullValue()));
  }

  @Test
  void getMyEnrollmentReturnsOk() throws Exception {
    given(enrollmentQueryService.getMyEnrollment(3L, "E1002"))
        .willReturn(
            new EnrollmentResponse(
                11L,
                3L,
                "E1002",
                EnrollmentStatus.WAITING,
                2,
                Instant.parse("2026-11-12T01:00:00Z")));

    mockMvc
        .perform(get(ME_URL).header(HEADER, "E1002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(11))
        .andExpect(jsonPath("$.sessionId").value(3))
        .andExpect(jsonPath("$.employeeId").value("E1002"))
        .andExpect(jsonPath("$.status").value("WAITING"))
        .andExpect(jsonPath("$.waitingNumber").value(2))
        .andExpect(jsonPath("$.appliedAt").value("2026-11-12T01:00:00Z"));
    verify(enrollmentQueryService).getMyEnrollment(3L, "E1002");
  }

  @Test
  void getMyEnrollmentRejectsMissingHeader() throws Exception {
    mockMvc
        .perform(get(ME_URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    verifyNoInteractions(enrollmentQueryService);
  }

  @Test
  void getMyEnrollmentReturnsNotFoundWhenNoActiveEnrollment() throws Exception {
    given(enrollmentQueryService.getMyEnrollment(3L, "E1001"))
        .willThrow(new EnrollmentNotFoundException(3L));

    mockMvc
        .perform(get(ME_URL).header(HEADER, "E1001"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void cancelReturnsOkWithCancelledStatus() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willReturn(
            new EnrollmentResponse(
                10L,
                3L,
                "E1001",
                EnrollmentStatus.CANCELLED,
                null,
                Instant.parse("2026-11-12T01:00:00Z")));

    mockMvc
        .perform(post(CANCEL_URL).header(HEADER, "E1001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.waitingNumber").value(nullValue()));
    verify(enrollmentCommandService).cancel(3L, "E1001");
  }

  @Test
  void cancelRejectsMissingHeader() throws Exception {
    mockMvc
        .perform(post(CANCEL_URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void cancelReturnsNotFoundWhenNoActiveEnrollment() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willThrow(new EnrollmentNotFoundException(3L));

    mockMvc
        .perform(post(CANCEL_URL).header(HEADER, "E1001"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void cancelReturnsConflictWhenSessionStarted() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willThrow(new ConflictException("SESSION_STARTED", "교육이 시작된 회차는 취소할 수 없습니다: 3"));

    mockMvc
        .perform(post(CANCEL_URL).header(HEADER, "E1001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STARTED"));
  }

  @Test
  void cancelReturnsServiceUnavailableOnLockTimeout() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(CANCEL_URL).header(HEADER, "E1001"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
  }

  @Test
  void enrollRejectsMissingHeader() throws Exception {
    mockMvc
        .perform(post(URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void enrollRejectsBlankHeader() throws Exception {
    mockMvc
        .perform(post(URL).header(HEADER, "  "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void enrollRejectsTooLongHeader() throws Exception {
    mockMvc
        .perform(post(URL).header(HEADER, "E".repeat(21)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void enrollReturnsNotFoundWhenSessionMissing() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001")).willThrow(new SessionNotFoundException(3L));

    mockMvc
        .perform(post(URL).header(HEADER, "E1001"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void enrollReturnsConflictWhenSessionStarted() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willThrow(new ConflictException("SESSION_STARTED", "교육이 시작된 회차에는 신청할 수 없습니다: 3"));

    mockMvc
        .perform(post(URL).header(HEADER, "E1001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STARTED"));
  }

  @Test
  void enrollReturnsConflictWhenDuplicate() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willThrow(new DuplicateEnrollmentException(3L));

    mockMvc
        .perform(post(URL).header(HEADER, "E1001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
  }

  @Test
  void enrollReturnsServiceUnavailableOnLockTimeout() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(URL).header(HEADER, "E1001"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
  }
}
