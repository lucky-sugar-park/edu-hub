package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompletionServiceTest {

  private static final Instant NOW = Instant.parse("2026-12-01T05:00:00Z");
  private static final Long SESSION_ID = 3L;

  @Mock private CourseSessionRepository courseSessionRepository;
  @Mock private EnrollmentRepository enrollmentRepository;

  private CompletionService service;
  private CourseSession session;

  @BeforeEach
  void setUp() {
    service =
        new CompletionService(
            courseSessionRepository, enrollmentRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    session = new CourseSession(new Course("JPA 입문", "개발", null), NOW.minusSeconds(3600), "강의실", 5);
    ReflectionTestUtils.setField(session, "id", SESSION_ID);
  }

  private void givenSession(CourseSession found) {
    given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.of(found));
  }

  private void givenClosedSession() {
    session.close(NOW);
    givenSession(session);
  }

  private void givenRows(Enrollment... rows) {
    given(enrollmentRepository.findBySessionIdAndEmployeeIdIn(anyLong(), anyCollection()))
        .willReturn(List.of(rows));
  }

  private Enrollment confirmed(String employeeId) {
    return Enrollment.confirmed(session, employeeId, NOW.minusSeconds(7200));
  }

  @Test
  void rejectsSessionNotStartedWithoutReadingEnrollments() {
    CourseSession future =
        new CourseSession(new Course("JPA 입문", "개발", null), NOW.plusSeconds(60), "강의실", 5);
    givenSession(future);

    assertThatThrownBy(() -> service.complete(SESSION_ID, List.of("E1")))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_NOT_STARTED"));

    verifyNoInteractions(enrollmentRepository);
  }

  @Test
  void rejectsOpenSessionWithoutReadingEnrollments() {
    givenSession(session);

    assertThatThrownBy(() -> service.complete(SESSION_ID, List.of("E1")))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_NOT_CLOSED"));

    verifyNoInteractions(enrollmentRepository);
  }

  @Test
  void throwsNotFoundWhenSessionMissing() {
    given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.complete(SESSION_ID, List.of("E1")))
        .isInstanceOf(SessionNotFoundException.class);

    verifyNoInteractions(enrollmentRepository);
  }

  @Test
  void usesLockedLookupAndNeverPlainFindById() {
    givenClosedSession();
    givenRows();

    service.complete(SESSION_ID, List.of("E1"));

    verify(courseSessionRepository).findByIdForUpdate(SESSION_ID);
    verify(courseSessionRepository, never()).findById(any());
    verify(courseSessionRepository, never()).existsById(any());
  }

  @Test
  void completesConfirmedAndReportsFailuresInRequestOrder() {
    givenClosedSession();
    Enrollment ok1 = confirmed("E1");
    Enrollment waiting = Enrollment.waiting(session, "E2", NOW.minusSeconds(100), 1L);
    Enrollment done = confirmed("E3");
    done.complete(NOW.minusSeconds(50));
    Enrollment cancelled = confirmed("E4");
    cancelled.cancel(NOW.minusSeconds(60));
    Enrollment ok2 = confirmed("E6");
    givenRows(ok1, waiting, done, cancelled, ok2);

    CompletionResponse response =
        service.complete(SESSION_ID, List.of("E6", "E2", "E1", "E3", "E4", "E5"));

    assertThat(response.completed()).containsExactly("E6", "E1");
    assertThat(response.failed())
        .extracting(CompletionResponse.Failure::employeeId, CompletionResponse.Failure::reason)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("E2", CompletionFailureReason.NOT_CONFIRMED),
            org.assertj.core.groups.Tuple.tuple("E3", CompletionFailureReason.ALREADY_COMPLETED),
            org.assertj.core.groups.Tuple.tuple("E4", CompletionFailureReason.CANCELLED),
            org.assertj.core.groups.Tuple.tuple("E5", CompletionFailureReason.NOT_ENROLLED));
    assertThat(ok1.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(ok1.getCompletedAt()).isEqualTo(NOW);
    assertThat(ok2.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    // 대기자와 그 밖의 행은 바뀌지 않는다(D-09, D-10).
    assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(waiting.getWaitingKey()).isEqualTo(1L);
    assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    assertThat(done.getCompletedAt()).isEqualTo(NOW.minusSeconds(50));
  }

  @Test
  void savesOnlyCompletedRows() {
    givenClosedSession();
    Enrollment ok = confirmed("E1");
    Enrollment waiting = Enrollment.waiting(session, "E2", NOW, 1L);
    givenRows(ok, waiting);

    service.complete(SESSION_ID, List.of("E1", "E2"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Enrollment>> saved = ArgumentCaptor.forClass(Collection.class);
    verify(enrollmentRepository).saveAllAndFlush(saved.capture());
    assertThat(saved.getValue()).containsExactly(ok);
  }

  @Test
  void reEnrolledEmployeeWithCancelledAndConfirmedRowsIsCompleted() {
    givenClosedSession();
    Enrollment oldCancelled = confirmed("E1");
    oldCancelled.cancel(NOW.minusSeconds(500));
    Enrollment current = confirmed("E1");
    // 조회 결과의 순서와 관계없이 활성 행이 우선이다.
    givenRows(oldCancelled, current);

    CompletionResponse response = service.complete(SESSION_ID, List.of("E1"));

    assertThat(response.completed()).containsExactly("E1");
    assertThat(response.failed()).isEmpty();
    assertThat(current.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(oldCancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
  }

  @Test
  void completedRowBeatsCancelledRowAndActiveBeatsCompleted() {
    givenClosedSession();
    Enrollment cancelled = confirmed("E1");
    cancelled.cancel(NOW.minusSeconds(500));
    Enrollment done = confirmed("E1");
    done.complete(NOW.minusSeconds(100));
    givenRows(cancelled, done);

    CompletionResponse response = service.complete(SESSION_ID, List.of("E1"));

    assertThat(response.failed())
        .extracting(CompletionResponse.Failure::reason)
        .containsExactly(CompletionFailureReason.ALREADY_COMPLETED);
  }

  @Test
  void duplicateEmployeeIdsAreProcessedOnceAndQueriedOnce() {
    givenClosedSession();
    Enrollment ok = confirmed("E1");
    givenRows(ok);

    CompletionResponse response = service.complete(SESSION_ID, List.of("E1", "E1", "E9", "E9"));

    assertThat(response.completed()).containsExactly("E1");
    assertThat(response.failed())
        .extracting(CompletionResponse.Failure::employeeId)
        .containsExactly("E9");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
    verify(enrollmentRepository).findBySessionIdAndEmployeeIdIn(anyLong(), ids.capture());
    assertThat(ids.getValue()).containsExactly("E1", "E9");
  }
}
