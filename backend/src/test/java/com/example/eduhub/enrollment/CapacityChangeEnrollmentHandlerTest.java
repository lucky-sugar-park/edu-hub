package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CapacityChangeEnrollmentHandlerTest {

  private static final Long SESSION_ID = 3L;
  private static final Instant T1 = Instant.parse("2026-11-12T01:00:00Z");

  @Mock private EnrollmentRepository enrollmentRepository;

  private CapacityChangeEnrollmentHandler handler;
  private CourseSession session;

  @BeforeEach
  void setUp() {
    handler = new CapacityChangeEnrollmentHandler(enrollmentRepository);
    session =
        new CourseSession(
            new Course("JPA 입문", "개발", null), Instant.parse("2027-01-01T00:00:00Z"), "본사", 5);
    ReflectionTestUtils.setField(session, "id", SESSION_ID);
  }

  /** D1 */
  @Test
  void demotesLatestConfirmedFirstWithEarlierConfirmedAheadAndBeforeExistingWaiters() {
    Enrollment e4 = confirmed(14L, T1.plusSeconds(4));
    Enrollment e5 = confirmed(15L, T1.plusSeconds(5));
    givenConfirmedCount(5);
    // 쿼리 결과는 늦게 확정된 순(t5, t4)
    givenTargets(2, List.of(e5, e4));
    given(enrollmentRepository.findMinWaitingKey(SESSION_ID)).willReturn(Optional.of(1L));

    handler.onCapacityChanged(session, 5, 3);

    assertThat(e4.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(e5.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(e4.getWaitingKey()).isLessThan(e5.getWaitingKey());
    assertThat(e5.getWaitingKey()).isLessThan(1L);
    assertThat(e4.getConfirmedAt()).isNull();
    verify(enrollmentRepository).saveAllAndFlush(List.of(e4, e5));
  }

  /** D2 */
  @Test
  void assignsKeysBelowExistingMinimum() {
    Enrollment e4 = confirmed(14L, T1.plusSeconds(4));
    Enrollment e5 = confirmed(15L, T1.plusSeconds(5));
    givenConfirmedCount(5);
    givenTargets(2, List.of(e5, e4));
    given(enrollmentRepository.findMinWaitingKey(SESSION_ID)).willReturn(Optional.of(1L));

    handler.onCapacityChanged(session, 5, 3);

    assertThat(e4.getWaitingKey()).isEqualTo(-1L);
    assertThat(e5.getWaitingKey()).isEqualTo(0L);
  }

  /** D3 */
  @Test
  void usesKeyZeroWhenNoWaiters() {
    Enrollment e = confirmed(14L, T1);
    givenConfirmedCount(3);
    givenTargets(1, List.of(e));
    given(enrollmentRepository.findMinWaitingKey(SESSION_ID)).willReturn(Optional.empty());

    handler.onCapacityChanged(session, 3, 2);

    assertThat(e.getWaitingKey()).isEqualTo(0L);
    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
  }

  /** D4 */
  @Test
  void doesNothingWhenConfirmedWithinNewCapacity() {
    givenConfirmedCount(3);

    handler.onCapacityChanged(session, 5, 3);

    verify(enrollmentRepository, never())
        .findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(anyLong(), any(), any());
    verify(enrollmentRepository, never()).saveAllAndFlush(any());
  }

  /** D5 */
  @Test
  void doesNothingOnCapacityIncrease() {
    handler.onCapacityChanged(session, 3, 5);

    verifyNoInteractions(enrollmentRepository);
  }

  /** D6: 정렬(확정 시각 동률이면 id 큰 순)은 쿼리(EnrollmentRepositoryTest)에 위임하고, 여기서는 반환된 대상만 강등하는지 확인한다. */
  @Test
  void requestsTieBreakByIdDescendingAndKeepsSmallerIdAhead() {
    Enrollment small = confirmed(14L, T1);
    Enrollment large = confirmed(15L, T1);
    givenConfirmedCount(3);
    // 정렬 결과: id 큰 쪽이 먼저 나온다. 1명만 강등하면 큰 쪽이다.
    givenTargets(1, List.of(large));

    handler.onCapacityChanged(session, 3, 2);

    assertThat(large.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(small.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
  }

  @Test
  void demotesExactlyExcessCountFromPageRequest() {
    Enrollment e = confirmed(14L, T1);
    givenConfirmedCount(4);
    givenTargets(1, List.of(e));

    handler.onCapacityChanged(session, 5, 3);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(enrollmentRepository)
        .findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(
            org.mockito.ArgumentMatchers.eq(SESSION_ID),
            org.mockito.ArgumentMatchers.eq(EnrollmentStatus.CONFIRMED),
            captor.capture());
    assertThat(captor.getValue()).isEqualTo(PageRequest.of(0, 1));
  }

  private Enrollment confirmed(long id, Instant confirmedAt) {
    Enrollment enrollment = Enrollment.confirmed(session, "E" + id, confirmedAt);
    ReflectionTestUtils.setField(enrollment, "id", id);
    return enrollment;
  }

  private void givenConfirmedCount(long count) {
    given(enrollmentRepository.countBySessionIdAndStatus(SESSION_ID, EnrollmentStatus.CONFIRMED))
        .willReturn(count);
  }

  private void givenTargets(int n, List<Enrollment> result) {
    given(
            enrollmentRepository.findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(
                SESSION_ID, EnrollmentStatus.CONFIRMED, PageRequest.of(0, n)))
        .willReturn(result);
  }
}
