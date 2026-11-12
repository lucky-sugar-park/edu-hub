package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentCommandServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
  private static final Instant FUTURE = NOW.plus(Duration.ofDays(7));
  private static final Long SESSION_ID = 3L;
  private static final String EMPLOYEE_ID = "E1001";

  @Mock private CourseSessionRepository courseSessionRepository;
  @Mock private EnrollmentRepository enrollmentRepository;

  private EnrollmentCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new EnrollmentCommandService(
            courseSessionRepository, enrollmentRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private CourseSession givenSession(Course course, Instant startAt, int capacity) {
    CourseSession session = new CourseSession(course, startAt, "강의실", capacity);
    ReflectionTestUtils.setField(session, "id", SESSION_ID);
    given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.of(session));
    return session;
  }

  private CourseSession givenOpenSession(int capacity) {
    return givenSession(new Course("JPA 입문", "개발", null), FUTURE, capacity);
  }

  private void givenSaveReturnsArgument() {
    given(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
        .willAnswer(call -> call.getArgument(0));
  }

  private void givenConfirmedCount(long count) {
    givenCounts(count, 0);
  }

  private void givenCounts(long confirmed, long waiting) {
    given(enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(anyLong(), any(), any()))
        .willReturn(false);
    given(enrollmentRepository.countBySessionIdAndStatus(SESSION_ID, EnrollmentStatus.CONFIRMED))
        .willReturn(confirmed);
    given(enrollmentRepository.countBySessionIdAndStatus(SESSION_ID, EnrollmentStatus.WAITING))
        .willReturn(waiting);
  }

  @Test
  void w1FirstWaiterGetsKeyOneAndNumberOne() {
    givenOpenSession(1);
    givenCounts(1, 0);
    given(enrollmentRepository.findMaxWaitingKey(SESSION_ID)).willReturn(Optional.empty());
    given(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                SESSION_ID, EnrollmentStatus.WAITING, 1L))
        .willReturn(0L);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(saved.getValue().getWaitingKey()).isEqualTo(1L);
    assertThat(saved.getValue().getConfirmedAt()).isNull();
    assertThat(response.waitingNumber()).isEqualTo(1);
  }

  @Test
  void w2WaiterGetsMaxKeyPlusOneAndNumberAfterThoseAhead() {
    givenOpenSession(1);
    givenCounts(1, 3);
    given(enrollmentRepository.findMaxWaitingKey(SESSION_ID)).willReturn(Optional.of(5L));
    given(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                SESSION_ID, EnrollmentStatus.WAITING, 6L))
        .willReturn(3L);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getWaitingKey()).isEqualTo(6L);
    assertThat(response.waitingNumber()).isEqualTo(4);
  }

  @Test
  void w3ConfirmedHasNoWaitingNumberAndConfirmedAtIsNow() {
    givenOpenSession(10);
    givenConfirmedCount(0);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getConfirmedAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getWaitingKey()).isNull();
    assertThat(response.waitingNumber()).isNull();
    verify(enrollmentRepository, never()).findMaxWaitingKey(anyLong());
    verify(enrollmentRepository, never())
        .countBySessionIdAndStatusAndWaitingKeyLessThan(anyLong(), any(), any());
  }

  @Test
  void s12WaitsWhenSeatsRemainButSomeoneIsWaiting() {
    givenOpenSession(2);
    givenCounts(1, 1);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITING);
  }

  @Test
  void s1ConfirmsWhenConfirmedCountIsBelowCapacity() {
    givenOpenSession(10);
    givenConfirmedCount(9);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(saved.getValue().getEmployeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(saved.getValue().getAppliedAt()).isEqualTo(NOW);
    assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
    assertThat(response.employeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(response.appliedAt()).isEqualTo(NOW);
  }

  @Test
  void s2WaitsWhenConfirmedCountEqualsCapacity() {
    givenOpenSession(10);
    givenConfirmedCount(10);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITING);
  }

  @Test
  void s3ConfirmsLastSeatOfCapacityOne() {
    givenOpenSession(1);
    givenConfirmedCount(0);
    givenSaveReturnsArgument();

    EnrollmentResponse response = service.enroll(SESSION_ID, EMPLOYEE_ID);

    assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
  }

  @Test
  void s4ThrowsWhenSessionNotFound() {
    given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOf(SessionNotFoundException.class);

    verify(enrollmentRepository, never()).saveAndFlush(any());
  }

  @Test
  void s5RejectsClosedSession() {
    CourseSession session =
        givenSession(new Course("JPA 입문", "개발", null), NOW.minusSeconds(60), 10);
    session.close(NOW);

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_CLOSED"));

    verify(enrollmentRepository, never()).saveAndFlush(any());
  }

  @Test
  void s6RejectsSessionStartingExactlyNow() {
    givenSession(new Course("JPA 입문", "개발", null), NOW, 10);

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_STARTED"));

    verify(enrollmentRepository, never()).saveAndFlush(any());
  }

  @Test
  void s7RejectsClosedCourse() {
    Course course = new Course("JPA 입문", "개발", null);
    course.close();
    givenSession(course, FUTURE, 10);

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("COURSE_CLOSED"));

    verify(enrollmentRepository, never()).saveAndFlush(any());
  }

  @Test
  void s8RejectsDuplicateActiveEnrollment() {
    givenOpenSession(10);
    given(enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(anyLong(), any(), any()))
        .willReturn(true);

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOf(DuplicateEnrollmentException.class)
        .isInstanceOfSatisfying(
            ConflictException.class,
            e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_ENROLLMENT"));

    verify(enrollmentRepository, never()).saveAndFlush(any());
    verify(enrollmentRepository, never()).countBySessionIdAndStatus(anyLong(), any());
  }

  @Test
  void s9ConvertsActiveUniqueViolationToDuplicate() {
    givenOpenSession(10);
    givenConfirmedCount(0);
    given(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
        .willThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException(
                    "Duplicate entry '3-E1001' for key 'UK_ENROLLMENT_ACTIVE'")));

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
        .isInstanceOf(DuplicateEnrollmentException.class);
  }

  @Test
  void s10UsesLockingLookupNotFindById() {
    givenOpenSession(10);
    givenConfirmedCount(0);
    givenSaveReturnsArgument();

    service.enroll(SESSION_ID, EMPLOYEE_ID);

    verify(courseSessionRepository).findByIdForUpdate(SESSION_ID);
    verify(courseSessionRepository, never()).findById(any());
    verifyNoMoreInteractions(courseSessionRepository);
  }

  @Test
  void s11PropagatesOtherIntegrityViolations() {
    givenOpenSession(10);
    givenConfirmedCount(0);
    DataIntegrityViolationException other =
        new DataIntegrityViolationException(
            "could not execute statement",
            new SQLIntegrityConstraintViolationException("Column 'employee_id' cannot be null"));
    given(enrollmentRepository.saveAndFlush(any(Enrollment.class))).willThrow(other);

    assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID)).isSameAs(other);
  }
}
