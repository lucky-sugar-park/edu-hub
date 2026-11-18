package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.eduhub.common.PageResponse;
import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
  private static final Long SESSION_ID = 3L;
  private static final String EMPLOYEE_ID = "E1001";

  @Mock private CourseSessionRepository courseSessionRepository;
  @Mock private EnrollmentRepository enrollmentRepository;

  private EnrollmentQueryService service;
  private CourseSession session;

  @BeforeEach
  void setUp() {
    service =
        new EnrollmentQueryService(
            courseSessionRepository,
            enrollmentRepository,
            new WaitingNumbers(enrollmentRepository));
    session = new CourseSession(new Course("JPA 입문", "개발", null), NOW.plusSeconds(3600), "강의실", 5);
    ReflectionTestUtils.setField(session, "id", SESSION_ID);
  }

  private void givenActive(Enrollment enrollment) {
    given(courseSessionRepository.existsById(SESSION_ID)).willReturn(true);
    given(
            enrollmentRepository.findBySessionIdAndEmployeeIdAndStatusIn(
                eq(SESSION_ID), eq(EMPLOYEE_ID), any()))
        .willReturn(Optional.ofNullable(enrollment));
  }

  @Test
  void confirmedHasNoWaitingNumber() {
    givenActive(Enrollment.confirmed(session, EMPLOYEE_ID, NOW));

    EnrollmentResponse response = service.getMyEnrollment(SESSION_ID, EMPLOYEE_ID);

    assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(response.waitingNumber()).isNull();
    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
    verify(enrollmentRepository, never())
        .countBySessionIdAndStatusAndWaitingKeyLessThan(anyLong(), any(), any());
  }

  @Test
  void waitingComputesNumberFromKeysAhead() {
    givenActive(Enrollment.waiting(session, EMPLOYEE_ID, NOW, 7L));
    given(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                SESSION_ID, EnrollmentStatus.WAITING, 7L))
        .willReturn(2L);

    EnrollmentResponse response = service.getMyEnrollment(SESSION_ID, EMPLOYEE_ID);

    assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(response.waitingNumber()).isEqualTo(3);
  }

  @Test
  void throwsWhenNoActiveEnrollment() {
    givenActive(null);

    assertThatThrownBy(() -> service.getMyEnrollment(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOf(EnrollmentNotFoundException.class)
        .hasMessageNotContaining(EMPLOYEE_ID);
  }

  @Test
  void throwsWhenSessionNotFound() {
    given(courseSessionRepository.existsById(SESSION_ID)).willReturn(false);

    assertThatThrownBy(() -> service.getMyEnrollment(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOf(SessionNotFoundException.class);

    verify(enrollmentRepository, never())
        .findBySessionIdAndEmployeeIdAndStatusIn(anyLong(), any(), any());
  }

  @Test
  void myEnrollmentsDropsClientSortAndConvertsRows() {
    Instant start = NOW.plusSeconds(3600);
    var waitingRow =
        new MyEnrollmentRow(2L, "JPA 입문", 3L, start, "강의실", EnrollmentStatus.WAITING, 4L, NOW);
    var confirmedRow =
        new MyEnrollmentRow(1L, "JPA 입문", 4L, start, "강의실", EnrollmentStatus.CONFIRMED, null, NOW);
    Pageable requested = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "id"));
    given(enrollmentRepository.findMyEnrollments(eq(EMPLOYEE_ID), eq(null), any()))
        .willReturn(new PageImpl<>(List.of(waitingRow, confirmedRow), PageRequest.of(1, 5), 7));

    PageResponse<MyEnrollmentResponse> response =
        service.getMyEnrollments(EMPLOYEE_ID, null, requested);

    ArgumentCaptor<Pageable> captured = ArgumentCaptor.forClass(Pageable.class);
    verify(enrollmentRepository).findMyEnrollments(eq(EMPLOYEE_ID), eq(null), captured.capture());
    assertThat(captured.getValue()).isEqualTo(PageRequest.of(1, 5));
    assertThat(captured.getValue().getSort().isUnsorted()).isTrue();
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(5);
    assertThat(response.totalElements()).isEqualTo(7);
    assertThat(response.content()).hasSize(2);
    assertThat(response.content().get(0).waitingNumber()).isEqualTo(4);
    assertThat(response.content().get(0).courseTitle()).isEqualTo("JPA 입문");
    assertThat(response.content().get(0).sessionStartAt()).isEqualTo(start);
    assertThat(response.content().get(1).waitingNumber()).isNull();
  }

  @Test
  void myEnrollmentsPassesStatusFilter() {
    given(enrollmentRepository.findMyEnrollments(any(), any(), any()))
        .willReturn(new PageImpl<>(List.of()));

    service.getMyEnrollments(EMPLOYEE_ID, EnrollmentStatus.WAITING, PageRequest.of(0, 20));

    verify(enrollmentRepository)
        .findMyEnrollments(EMPLOYEE_ID, EnrollmentStatus.WAITING, PageRequest.of(0, 20));
  }

  @Test
  void looksUpOnlyActiveStatuses() {
    givenActive(Enrollment.confirmed(session, EMPLOYEE_ID, NOW));

    service.getMyEnrollment(SESSION_ID, EMPLOYEE_ID);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<EnrollmentStatus>> statuses =
        ArgumentCaptor.forClass(Collection.class);
    verify(enrollmentRepository)
        .findBySessionIdAndEmployeeIdAndStatusIn(
            eq(SESSION_ID), eq(EMPLOYEE_ID), statuses.capture());
    assertThat(statuses.getValue())
        .containsExactlyInAnyOrderElementsOf(
            Set.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING));
  }
}
