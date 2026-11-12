package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.common.InvalidRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseCommandServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
  private static final Instant FUTURE = NOW.plus(Duration.ofDays(7));
  private static final Instant PAST = NOW.minus(Duration.ofDays(1));

  @Mock private CourseRepository courseRepository;
  @Mock private CourseSessionRepository courseSessionRepository;

  @Mock private SessionSeatCounter sessionSeatCounter;

  private CourseCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new CourseCommandService(
            courseRepository,
            courseSessionRepository,
            sessionSeatCounter,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Nested
  class CourseCommands {

    @Test
    void createCourseSavesOpenCourse() {
      given(courseRepository.save(any(Course.class))).willAnswer(call -> call.getArgument(0));

      CourseDetailResponse response = service.createCourse(new CourseRequest("JPA 입문", "개발", "기초"));

      assertThat(response.title()).isEqualTo("JPA 입문");
      assertThat(response.category()).isEqualTo("개발");
      assertThat(response.description()).isEqualTo("기초");
      assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
      assertThat(response.sessions()).isEmpty();
    }

    @Test
    void updateCourseChangesFields() {
      Course course = givenCourse(1L, new Course("JPA 입문", "개발", null));

      CourseDetailResponse response =
          service.updateCourse(1L, new CourseRequest("JPA 심화", "개발", "심화 과정"));

      assertThat(response.title()).isEqualTo("JPA 심화");
      assertThat(course.getDescription()).isEqualTo("심화 과정");
    }

    @Test
    void updateCourseFailsWhenCourseMissing() {
      given(courseRepository.findById(9L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateCourse(9L, new CourseRequest("t", "c", null)))
          .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void updateCourseFailsWhenClosed() {
      Course course = new Course("JPA 입문", "개발", null);
      course.close();
      givenCourse(1L, course);

      assertConflict(
          () -> service.updateCourse(1L, new CourseRequest("t", "c", null)), "COURSE_CLOSED");
    }

    @Test
    void closeCourseKeepsSessionsOpen() {
      Course course = givenCourse(1L, new Course("JPA 입문", "개발", null));
      CourseSession session = new CourseSession(course, FUTURE, "본사", 10);
      given(courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(any()))
          .willReturn(List.of(session));

      CourseDetailResponse response = service.closeCourse(1L);

      assertThat(response.status()).isEqualTo(CourseStatus.CLOSED);
      assertThat(response.sessions())
          .extracting(SessionResponse::status)
          .containsExactly(CourseSessionStatus.OPEN);
    }

    @Test
    void closeCourseFailsWhenAlreadyClosed() {
      Course course = new Course("JPA 입문", "개발", null);
      course.close();
      givenCourse(1L, course);

      assertConflict(() -> service.closeCourse(1L), "COURSE_CLOSED");
    }
  }

  @Nested
  class SessionCommands {

    @Test
    void createSessionSavesOpenSession() {
      givenCourse(1L, new Course("JPA 입문", "개발", null));
      given(courseSessionRepository.save(any(CourseSession.class)))
          .willAnswer(call -> call.getArgument(0));

      SessionResponse response = service.createSession(1L, new SessionRequest(FUTURE, "본사", 20));

      assertThat(response.startAt()).isEqualTo(FUTURE);
      assertThat(response.place()).isEqualTo("본사");
      assertThat(response.capacity()).isEqualTo(20);
      assertThat(response.status()).isEqualTo(CourseSessionStatus.OPEN);
    }

    @Test
    void createSessionFailsWhenCourseMissing() {
      given(courseRepository.findById(9L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.createSession(9L, new SessionRequest(FUTURE, "본사", 20)))
          .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void createSessionFailsWhenCourseClosed() {
      Course course = new Course("JPA 입문", "개발", null);
      course.close();
      givenCourse(1L, course);

      assertConflict(
          () -> service.createSession(1L, new SessionRequest(FUTURE, "본사", 20)), "COURSE_CLOSED");
      verify(courseSessionRepository, never()).save(any());
    }

    @Test
    void createSessionFailsWhenStartIsPast() {
      givenCourse(1L, new Course("JPA 입문", "개발", null));

      assertThatThrownBy(() -> service.createSession(1L, new SessionRequest(PAST, "본사", 20)))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("startAt: 현재 이후 시각이어야 합니다");
    }

    @Test
    void updateSessionBeforeStartChangesAllFields() {
      CourseSession session = givenSession(5L, FUTURE);
      Instant later = FUTURE.plus(Duration.ofDays(1));

      SessionResponse response = service.updateSession(5L, new SessionRequest(later, "별관", 30));

      assertThat(response.startAt()).isEqualTo(later);
      assertThat(response.place()).isEqualTo("별관");
      assertThat(session.getCapacity()).isEqualTo(30);
    }

    @Test
    void updateSessionReflectsConfirmedCountInRemainingSeats() {
      CourseSession session = givenSession(5L, FUTURE);
      ReflectionTestUtils.setField(session, "id", 5L);
      given(sessionSeatCounter.countConfirmedBySessionIds(List.of(5L))).willReturn(Map.of(5L, 4L));

      SessionResponse response = service.updateSession(5L, new SessionRequest(FUTURE, "본사", 10));

      assertThat(response.capacity()).isEqualTo(10);
      assertThat(response.remainingSeats()).isEqualTo(6);
    }

    @Test
    void updateSessionBeforeStartRejectsPastStart() {
      givenSession(5L, FUTURE);

      assertThatThrownBy(() -> service.updateSession(5L, new SessionRequest(PAST, "본사", 10)))
          .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateSessionAfterStartAllowsPlaceChange() {
      CourseSession session = givenSession(5L, PAST);

      service.updateSession(5L, new SessionRequest(PAST, "별관", 10));

      assertThat(session.getPlace()).isEqualTo("별관");
    }

    @Test
    void updateSessionAfterStartRejectsCapacityChange() {
      CourseSession session = givenSession(5L, PAST);

      assertConflict(
          () -> service.updateSession(5L, new SessionRequest(PAST, "본사", 11)), "SESSION_STARTED");
      assertThat(session.getCapacity()).isEqualTo(10);
    }

    @Test
    void updateSessionAfterStartRejectsStartChange() {
      givenSession(5L, PAST);

      assertConflict(
          () -> service.updateSession(5L, new SessionRequest(FUTURE, "본사", 10)), "SESSION_STARTED");
    }

    @Test
    void updateSessionTreatsStartTimeAsStarted() {
      givenSession(5L, NOW);

      assertConflict(
          () -> service.updateSession(5L, new SessionRequest(NOW, "본사", 11)), "SESSION_STARTED");
    }

    @Test
    void updateSessionFailsWhenClosed() {
      CourseSession session = givenSession(5L, PAST);
      session.close(NOW);

      assertConflict(
          () -> service.updateSession(5L, new SessionRequest(PAST, "별관", 10)), "SESSION_CLOSED");
    }

    @Test
    void updateSessionFailsWhenMissing() {
      given(courseSessionRepository.findById(9L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateSession(9L, new SessionRequest(FUTURE, "본사", 10)))
          .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void closeSessionAfterStartCompletesSession() {
      givenSession(5L, PAST);

      SessionResponse response = service.closeSession(5L);

      assertThat(response.status()).isEqualTo(CourseSessionStatus.CLOSED);
    }

    @Test
    void closeSessionBeforeStartFails() {
      givenSession(5L, FUTURE);

      assertConflict(() -> service.closeSession(5L), "SESSION_NOT_STARTED");
    }

    @Test
    void closeSessionFailsWhenAlreadyClosed() {
      givenSession(5L, PAST).close(NOW);

      assertConflict(() -> service.closeSession(5L), "SESSION_CLOSED");
    }

    @Test
    void closeSessionWorksEvenIfCourseClosed() {
      CourseSession session = givenSession(5L, PAST);
      session.getCourse().close();

      assertThat(service.closeSession(5L).status()).isEqualTo(CourseSessionStatus.CLOSED);
    }

    @Test
    void closeSessionFailsWhenMissing() {
      given(courseSessionRepository.findById(9L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.closeSession(9L))
          .isInstanceOf(SessionNotFoundException.class);
    }
  }

  private Course givenCourse(Long id, Course course) {
    given(courseRepository.findById(id)).willReturn(Optional.of(course));
    return course;
  }

  private CourseSession givenSession(Long id, Instant startAt) {
    CourseSession session = new CourseSession(new Course("JPA 입문", "개발", null), startAt, "본사", 10);
    given(courseSessionRepository.findById(id)).willReturn(Optional.of(session));
    return session;
  }

  private static void assertConflict(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOf(ConflictException.class)
        .extracting(error -> ((ConflictException) error).getCode())
        .isEqualTo(code);
  }
}
