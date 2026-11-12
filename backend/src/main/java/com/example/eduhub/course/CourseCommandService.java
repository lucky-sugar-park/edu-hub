package com.example.eduhub.course;

import com.example.eduhub.common.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 과정·회차 등록, 수정, 종료(FR-03). 조회는 {@link CourseQueryService}가 맡는다. */
@Service
@Transactional
public class CourseCommandService {

  private final CourseRepository courseRepository;
  private final CourseSessionRepository courseSessionRepository;
  private final SessionSeatCounter sessionSeatCounter;
  private final Clock clock;

  public CourseCommandService(
      CourseRepository courseRepository,
      CourseSessionRepository courseSessionRepository,
      SessionSeatCounter sessionSeatCounter,
      Clock clock) {
    this.courseRepository = courseRepository;
    this.courseSessionRepository = courseSessionRepository;
    this.sessionSeatCounter = sessionSeatCounter;
    this.clock = clock;
  }

  public CourseDetailResponse createCourse(CourseRequest request) {
    Course course =
        courseRepository.save(
            new Course(request.title(), request.category(), request.description()));
    return toDetail(course);
  }

  public CourseDetailResponse updateCourse(Long courseId, CourseRequest request) {
    Course course = findCourse(courseId);
    course.update(request.title(), request.category(), request.description());
    return toDetail(course);
  }

  public CourseDetailResponse closeCourse(Long courseId) {
    Course course = findCourse(courseId);
    course.close();
    return toDetail(course);
  }

  public SessionResponse createSession(Long courseId, SessionRequest request) {
    Course course = findCourse(courseId);
    course.ensureOpen();
    if (request.startAt().isBefore(now())) {
      throw new InvalidRequestException("startAt: 현재 이후 시각이어야 합니다");
    }
    CourseSession session =
        courseSessionRepository.save(
            new CourseSession(course, request.startAt(), request.place(), request.capacity()));
    // 새 회차라 확정 신청은 0이다.
    return SessionResponse.of(session, 0);
  }

  public SessionResponse updateSession(Long sessionId, SessionRequest request) {
    CourseSession session = findSession(sessionId);
    session.update(request.startAt(), request.place(), request.capacity(), now());
    // TODO(3주차, FR-05): 정원이 바뀌면 D-02(대기자 승급), D-03(확정자 강등)을 여기서 이어 처리한다.
    return toSessionResponse(session);
  }

  public SessionResponse closeSession(Long sessionId) {
    CourseSession session = findSession(sessionId);
    session.close(now());
    return toSessionResponse(session);
  }

  private Course findCourse(Long courseId) {
    return courseRepository
        .findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
  }

  private CourseSession findSession(Long sessionId) {
    return courseSessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new SessionNotFoundException(sessionId));
  }

  private CourseDetailResponse toDetail(Course course) {
    List<CourseSession> sessions =
        courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(course.getId());
    List<Long> sessionIds = sessions.stream().map(CourseSession::getId).toList();
    return CourseDetailResponse.of(
        course, sessions, sessionSeatCounter.countConfirmedBySessionIds(sessionIds));
  }

  private SessionResponse toSessionResponse(CourseSession session) {
    long confirmed =
        sessionSeatCounter
            .countConfirmedBySessionIds(Collections.singletonList(session.getId()))
            .getOrDefault(session.getId(), 0L);
    return SessionResponse.of(session, confirmed);
  }

  private Instant now() {
    return clock.instant();
  }
}
