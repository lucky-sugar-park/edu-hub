package com.example.eduhub.enrollment;

import static com.example.eduhub.auth.WebMvcSecurityTestConfig.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.auth.WebMvcSecurityTestConfig;
import com.example.eduhub.common.ConflictException;
import com.example.eduhub.common.PageResponse;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@Import(WebMvcSecurityTestConfig.class)
class EnrollmentControllerTest {

  private static final String URL = "/api/v1/sessions/3/enrollments";
  private static final String ME_URL = "/api/v1/sessions/3/enrollments/me";
  private static final String CANCEL_URL = "/api/v1/sessions/3/enrollments/me/cancel";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EnrollmentCommandService enrollmentCommandService;
  @MockitoBean private EnrollmentQueryService enrollmentQueryService;

  private static final String MY_LIST_URL = "/api/v1/me/enrollments";

  private static PageResponse<MyEnrollmentResponse> myPage() {
    return new PageResponse<>(
        List.of(
            new MyEnrollmentResponse(
                7L,
                5L,
                "JPA 입문",
                3L,
                Instant.parse("2026-12-01T01:00:00Z"),
                "본사 3층",
                EnrollmentStatus.WAITING,
                2,
                Instant.parse("2026-11-12T01:00:00Z"))),
        0,
        20,
        1);
  }

  @Test
  void myEnrollmentsReturnsPageAndPassesNullStatus() throws Exception {
    given(enrollmentQueryService.getMyEnrollments(eq("E1001"), isNull(), any()))
        .willReturn(myPage());

    mockMvc
        .perform(get(MY_LIST_URL).with(employee("E1001")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].courseId").value(5))
        .andExpect(jsonPath("$.content[0].courseTitle").value("JPA 입문"))
        .andExpect(jsonPath("$.content[0].waitingNumber").value(2))
        .andExpect(jsonPath("$.content[0].status").value("WAITING"))
        .andExpect(jsonPath("$.totalElements").value(1));
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(enrollmentQueryService).getMyEnrollments(eq("E1001"), isNull(), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  void myEnrollmentsCapsPageSizeAtMax() throws Exception {
    given(enrollmentQueryService.getMyEnrollments(eq("E1001"), isNull(), any()))
        .willReturn(myPage());

    mockMvc
        .perform(get(MY_LIST_URL).param("size", "100000").with(employee("E1001")))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(enrollmentQueryService).getMyEnrollments(eq("E1001"), isNull(), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void myEnrollmentsPassesStatusAsEnum() throws Exception {
    given(enrollmentQueryService.getMyEnrollments(eq("E1001"), eq(EnrollmentStatus.WAITING), any()))
        .willReturn(myPage());

    mockMvc
        .perform(get(MY_LIST_URL).param("status", "WAITING").with(employee("E1001")))
        .andExpect(status().isOk());
    verify(enrollmentQueryService)
        .getMyEnrollments(eq("E1001"), eq(EnrollmentStatus.WAITING), any());
  }

  @Test
  void myEnrollmentsRejectsMissingToken() throws Exception {
    mockMvc
        .perform(get(MY_LIST_URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentQueryService);
  }

  @Test
  void myEnrollmentsRejectsUnknownStatus() throws Exception {
    mockMvc
        .perform(get(MY_LIST_URL).param("status", "UNKNOWN").with(employee("E1001")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    verifyNoInteractions(enrollmentQueryService);
  }

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
        .perform(post(URL).with(employee("E1001")))
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
        .perform(post(URL).with(employee("E1002")))
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
        .perform(post(URL).with(employee("E1001")))
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
        .perform(get(ME_URL).with(employee("E1002")))
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
  void getMyEnrollmentRejectsMissingToken() throws Exception {
    mockMvc
        .perform(get(ME_URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentQueryService);
  }

  @Test
  void getMyEnrollmentReturnsNotFoundWhenNoActiveEnrollment() throws Exception {
    given(enrollmentQueryService.getMyEnrollment(3L, "E1001"))
        .willThrow(new EnrollmentNotFoundException(3L));

    mockMvc
        .perform(get(ME_URL).with(employee("E1001")))
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
        .perform(post(CANCEL_URL).with(employee("E1001")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.waitingNumber").value(nullValue()));
    verify(enrollmentCommandService).cancel(3L, "E1001");
  }

  @Test
  void cancelRejectsMissingToken() throws Exception {
    mockMvc
        .perform(post(CANCEL_URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void cancelReturnsNotFoundWhenNoActiveEnrollment() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willThrow(new EnrollmentNotFoundException(3L));

    mockMvc
        .perform(post(CANCEL_URL).with(employee("E1001")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void cancelReturnsConflictWhenSessionStarted() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willThrow(new ConflictException("SESSION_STARTED", "교육이 시작된 회차는 취소할 수 없습니다: 3"));

    mockMvc
        .perform(post(CANCEL_URL).with(employee("E1001")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STARTED"));
  }

  @Test
  void cancelReturnsServiceUnavailableOnLockTimeout() throws Exception {
    given(enrollmentCommandService.cancel(3L, "E1001"))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(CANCEL_URL).with(employee("E1001")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
  }

  @Test
  void enrollRejectsMissingToken() throws Exception {
    mockMvc
        .perform(post(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void enrollRejectsTokenWithBlankSubject() throws Exception {
    // 사번이 없는 토큰은 인증 정보가 없는 것으로 보고 401이다(리졸버의 방어 경로).
    mockMvc
        .perform(post(URL).with(employee("   ")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  @WithMockUser(username = "E1001")
  void enrollRejectsNonJwtAuthentication() throws Exception {
    // JWT가 아닌 인증(여기서는 mock user)은 사번을 꺼낼 수 없어 401이다(리졸버의 instanceof 분기).
    mockMvc
        .perform(post(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentCommandService);
  }

  @Test
  void enrollReturnsNotFoundWhenSessionMissing() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001")).willThrow(new SessionNotFoundException(3L));

    mockMvc
        .perform(post(URL).with(employee("E1001")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void enrollReturnsConflictWhenSessionStarted() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willThrow(new ConflictException("SESSION_STARTED", "교육이 시작된 회차에는 신청할 수 없습니다: 3"));

    mockMvc
        .perform(post(URL).with(employee("E1001")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STARTED"));
  }

  @Test
  void enrollReturnsConflictWhenDuplicate() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willThrow(new DuplicateEnrollmentException(3L));

    mockMvc
        .perform(post(URL).with(employee("E1001")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
  }

  @Test
  void enrollReturnsServiceUnavailableOnLockTimeout() throws Exception {
    given(enrollmentCommandService.enroll(3L, "E1001"))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(URL).with(employee("E1001")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
  }
}
