package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WaitingNumbersTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
  private static final Long SESSION_ID = 3L;

  @Mock private EnrollmentRepository enrollmentRepository;

  private WaitingNumbers waitingNumbers;
  private CourseSession session;

  @BeforeEach
  void setUp() {
    waitingNumbers = new WaitingNumbers(enrollmentRepository);
    session = new CourseSession(new Course("JPA 입문", "개발", null), NOW.plusSeconds(3600), "강의실", 5);
    ReflectionTestUtils.setField(session, "id", SESSION_ID);
  }

  @Test
  void waitingNumberIsKeysAheadPlusOne() {
    given(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                SESSION_ID, EnrollmentStatus.WAITING, 7L))
        .willReturn(3L);

    Integer number = waitingNumbers.numberOf(Enrollment.waiting(session, "E1001", NOW, 7L));

    assertThat(number).isEqualTo(4);
  }

  @Test
  void confirmedHasNoNumber() {
    Integer number = waitingNumbers.numberOf(Enrollment.confirmed(session, "E1001", NOW));

    assertThat(number).isNull();
    verify(enrollmentRepository, never())
        .countBySessionIdAndStatusAndWaitingKeyLessThan(anyLong(), any(), any());
  }

  @Test
  void cancelledHasNoNumber() {
    Enrollment enrollment = Enrollment.confirmed(session, "E1001", NOW);
    enrollment.cancel(NOW);

    assertThat(waitingNumbers.numberOf(enrollment)).isNull();
    verify(enrollmentRepository, never())
        .countBySessionIdAndStatusAndWaitingKeyLessThan(anyLong(), any(), any());
  }
}
