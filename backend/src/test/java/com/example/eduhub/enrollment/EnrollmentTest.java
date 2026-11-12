package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnrollmentTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
  private static final String EMPLOYEE_ID = "E1001";

  private final CourseSession session =
      new CourseSession(new Course("JPA 입문", "개발", null), NOW.plusSeconds(3600), "강의실", 10);

  @Test
  void confirmedFactorySetsConfirmedFields() {
    Enrollment enrollment = Enrollment.confirmed(session, EMPLOYEE_ID, NOW);

    assertThat(enrollment.getSession()).isSameAs(session);
    assertThat(enrollment.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
    assertThat(enrollment.getConfirmedAt()).isEqualTo(NOW);
    assertThat(enrollment.getWaitingKey()).isNull();
  }

  @Test
  void waitingFactorySetsWaitingFields() {
    Enrollment enrollment = Enrollment.waiting(session, EMPLOYEE_ID, NOW, 7L);

    assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
    assertThat(enrollment.getConfirmedAt()).isNull();
    assertThat(enrollment.getWaitingKey()).isEqualTo(7L);
  }

  @Test
  void demoteTurnsConfirmedIntoWaitingWithKey() {
    Enrollment enrollment = Enrollment.confirmed(session, EMPLOYEE_ID, NOW);

    enrollment.demote(-1L);

    assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(enrollment.getConfirmedAt()).isNull();
    assertThat(enrollment.getWaitingKey()).isEqualTo(-1L);
    assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
  }

  @Test
  void demoteRejectsWaitingEnrollment() {
    Enrollment enrollment = Enrollment.waiting(session, EMPLOYEE_ID, NOW, 3L);

    assertThatThrownBy(() -> enrollment.demote(1L)).isInstanceOf(IllegalStateException.class);
    assertThat(enrollment.getWaitingKey()).isEqualTo(3L);
  }
}
