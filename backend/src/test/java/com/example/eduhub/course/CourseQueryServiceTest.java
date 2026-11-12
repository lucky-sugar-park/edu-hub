package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.eduhub.common.PageResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTest {

  private static final Instant START_AT = Instant.parse("2026-12-01T01:00:00Z");

  @Mock private CourseRepository courseRepository;
  @Mock private CourseSessionRepository courseSessionRepository;
  @Mock private SessionSeatCounter sessionSeatCounter;

  private CourseQueryService service;

  @BeforeEach
  void setUp() {
    service = new CourseQueryService(courseRepository, courseSessionRepository, sessionSeatCounter);
  }

  @Test
  void remainingSeatsIsCapacityMinusConfirmed() {
    givenCourseWithSessions(session(11L, 10), session(12L, 20));
    given(sessionSeatCounter.countConfirmedBySessionIds(any()))
        .willReturn(Map.of(11L, 3L, 12L, 20L));

    CourseDetailResponse response = service.getCourse(1L);

    assertThat(response.sessions())
        .extracting(SessionResponse::remainingSeats)
        .containsExactly(7, 0);
  }

  @Test
  void sessionMissingFromCountsKeepsFullCapacity() {
    givenCourseWithSessions(session(11L, 10), session(12L, 5));
    given(sessionSeatCounter.countConfirmedBySessionIds(any())).willReturn(Map.of(11L, 2L));

    CourseDetailResponse response = service.getCourse(1L);

    assertThat(response.sessions())
        .extracting(SessionResponse::remainingSeats)
        .containsExactly(8, 5);
  }

  @Test
  void remainingSeatsNeverBelowZeroWhenConfirmedExceedsCapacity() {
    givenCourseWithSessions(session(11L, 10));
    given(sessionSeatCounter.countConfirmedBySessionIds(any())).willReturn(Map.of(11L, 12L));

    CourseDetailResponse response = service.getCourse(1L);

    assertThat(response.sessions().get(0).remainingSeats()).isZero();
  }

  @Test
  void countsAllSessionsInSingleCall() {
    givenCourseWithSessions(session(11L, 10), session(12L, 10), session(13L, 10));
    given(sessionSeatCounter.countConfirmedBySessionIds(any())).willReturn(Map.of());

    service.getCourse(1L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(sessionSeatCounter, times(1)).countConfirmedBySessionIds(captor.capture());
    assertThat(captor.getValue()).containsExactly(11L, 12L, 13L);
  }

  @Test
  void getCourseFailsWhenCourseMissing() {
    given(courseRepository.findById(9L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCourse(9L)).isInstanceOf(CourseNotFoundException.class);
  }

  @Test
  void searchCoursesDelegatesWithLatestFirstSort() {
    Page<Course> empty = new PageImpl<>(List.of());
    given(courseRepository.search(eq("jpa"), eq("개발"), any(Pageable.class))).willReturn(empty);

    PageResponse<CourseSummaryResponse> response =
        service.searchCourses("jpa", "개발", PageRequest.of(2, 5, Sort.by("title")));

    assertThat(response).isNotNull();
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(courseRepository).search(eq("jpa"), eq("개발"), captor.capture());
    assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
    assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
  }

  private void givenCourseWithSessions(CourseSession... sessions) {
    Course course = new Course("JPA 입문", "개발", null);
    given(courseRepository.findById(1L)).willReturn(Optional.of(course));
    given(courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(1L))
        .willReturn(List.of(sessions));
  }

  private static CourseSession session(Long id, int capacity) {
    CourseSession session =
        new CourseSession(new Course("JPA 입문", "개발", null), START_AT, "본사", capacity);
    ReflectionTestUtils.setField(session, "id", id);
    return session;
  }
}
