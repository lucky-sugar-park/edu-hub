package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VacancyFillerTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
  private static final Long SESSION_ID = 3L;

  @Mock private EnrollmentRepository enrollmentRepository;

  private VacancyFiller filler;

  @BeforeEach
  void setUp() {
    filler = new VacancyFiller(enrollmentRepository);
  }

  private CourseSession session(Instant startAt, int capacity) {
    CourseSession session =
        new CourseSession(new Course("JPA 입문", "개발", null), startAt, "강의실", capacity);
    ReflectionTestUtils.setField(session, "id", SESSION_ID);
    return session;
  }

  private Enrollment waiting(CourseSession session, String employeeId, long key) {
    return Enrollment.waiting(session, employeeId, NOW.minusSeconds(100), key);
  }

  private void givenConfirmed(long count) {
    given(enrollmentRepository.countBySessionIdAndStatus(SESSION_ID, EnrollmentStatus.CONFIRMED))
        .willReturn(count);
  }

  private void givenWaiters(List<Enrollment> waiters) {
    given(
            enrollmentRepository.findBySessionIdAndStatusOrderByWaitingKeyAsc(
                eq(SESSION_ID), eq(EnrollmentStatus.WAITING), any(Pageable.class)))
        .willReturn(waiters);
  }

  @Test
  void promotesAsManyAsVacanciesFromTheFrontOfTheQueue() {
    CourseSession session = session(NOW.plus(Duration.ofDays(1)), 5);
    givenConfirmed(3);
    List<Enrollment> firstTwo = List.of(waiting(session, "E1", -1L), waiting(session, "E2", 1L));
    givenWaiters(firstTwo);

    int promoted = filler.fillVacancies(session, NOW);

    assertThat(promoted).isEqualTo(2);
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(enrollmentRepository)
        .findBySessionIdAndStatusOrderByWaitingKeyAsc(
            eq(SESSION_ID), eq(EnrollmentStatus.WAITING), page.capture());
    assertThat(page.getValue().getPageNumber()).isZero();
    assertThat(page.getValue().getPageSize()).isEqualTo(2);
    verify(enrollmentRepository).saveAllAndFlush(firstTwo);
    assertThat(firstTwo)
        .allSatisfy(e -> {
          assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
          assertThat(e.getConfirmedAt()).isEqualTo(NOW);
          assertThat(e.getWaitingKey()).isNull();
        });
  }

  @Test
  void promotesOnlyWaitersWhenFewerThanVacancies() {
    CourseSession session = session(NOW.plus(Duration.ofDays(1)), 5);
    givenConfirmed(2);
    List<Enrollment> one = List.of(waiting(session, "E1", 1L));
    givenWaiters(one);

    assertThat(filler.fillVacancies(session, NOW)).isEqualTo(1);
    verify(enrollmentRepository).saveAllAndFlush(one);
  }

  @Test
  void noWaitersPromotesNothing() {
    CourseSession session = session(NOW.plus(Duration.ofDays(1)), 5);
    givenConfirmed(2);
    givenWaiters(List.of());

    assertThat(filler.fillVacancies(session, NOW)).isZero();
    verify(enrollmentRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void noVacanciesDoesNotQueryOrSave() {
    CourseSession session = session(NOW.plus(Duration.ofDays(1)), 5);
    givenConfirmed(5);

    assertThat(filler.fillVacancies(session, NOW)).isZero();

    verify(enrollmentRepository, never())
        .findBySessionIdAndStatusOrderByWaitingKeyAsc(anyLong(), any(), any());
    verify(enrollmentRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void promotesOnlyOneWhenExactlyOneVacancy() {
    CourseSession session = session(NOW.plus(Duration.ofDays(1)), 5);
    givenConfirmed(4);
    List<Enrollment> first = List.of(waiting(session, "E1", 1L));
    givenWaiters(first);

    assertThat(filler.fillVacancies(session, NOW)).isEqualTo(1);

    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(enrollmentRepository)
        .findBySessionIdAndStatusOrderByWaitingKeyAsc(
            eq(SESSION_ID), eq(EnrollmentStatus.WAITING), page.capture());
    assertThat(page.getValue().getPageSize()).isEqualTo(1);
    verify(enrollmentRepository).saveAllAndFlush(first);
  }

  @Test
  void confirmedOverCapacityDoesNotQueryOrSave() {
    CourseSession session = session(NOW.plus(Duration.ofDays(1)), 5);
    givenConfirmed(6);

    assertThat(filler.fillVacancies(session, NOW)).isZero();

    verify(enrollmentRepository, never())
        .findBySessionIdAndStatusOrderByWaitingKeyAsc(anyLong(), any(), any());
    verify(enrollmentRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void afterStartDoesNothing() {
    CourseSession session = session(NOW, 5);

    assertThat(filler.fillVacancies(session, NOW)).isZero();

    verifyNoMoreInteractions(enrollmentRepository);
  }
}
