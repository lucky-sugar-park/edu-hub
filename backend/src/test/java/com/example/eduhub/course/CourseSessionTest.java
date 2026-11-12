package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.common.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourseSessionTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

  private CourseSession sessionStartingAt(Instant startAt) {
    return new CourseSession(new Course("JPA 입문", "개발", null), startAt, "본사 3층", 20);
  }

  @Test
  void allowsEnrollmentBeforeStart() {
    CourseSession session = sessionStartingAt(NOW.plusSeconds(1));

    assertThatCode(() -> session.ensureEnrollable(NOW)).doesNotThrowAnyException();
  }

  @Test
  void rejectsEnrollmentAtStartTime() {
    CourseSession session = sessionStartingAt(NOW);

    assertThatThrownBy(() -> session.ensureEnrollable(NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_STARTED"));
  }

  @Test
  void rejectsEnrollmentAfterStart() {
    CourseSession session = sessionStartingAt(NOW.minusSeconds(1));

    assertThatThrownBy(() -> session.ensureEnrollable(NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_STARTED"));
  }

  @Test
  void checksClosedBeforeStarted() {
    CourseSession session = sessionStartingAt(NOW.minusSeconds(3600));
    session.close(NOW);

    assertThatThrownBy(() -> session.ensureEnrollable(NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_CLOSED"));
  }
}
