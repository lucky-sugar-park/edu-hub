package com.example.eduhub.course;

import com.example.eduhub.common.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 과정·회차 등록, 수정, 종료(FR-03). 조회는 {@link CourseQueryService}가 맡는다. */
@Service
@Transactional
public class CourseCommandService {

  private final CourseRepository courseRepository;
  private final CourseSessionRepository courseSessionRepository;
  private final SessionSeatCounter sessionSeatCounter;
  private final SessionCapacityChangeHandler capacityChangeHandler;
  private final Clock clock;

  public CourseCommandService(
      CourseRepository courseRepository,
      CourseSessionRepository courseSessionRepository,
      SessionSeatCounter sessionSeatCounter,
      SessionCapacityChangeHandler capacityChangeHandler,
      Clock clock) {
    this.capacityChangeHandler = capacityChangeHandler;
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

  /** 회차 락을 첫 쿼리로 잡아 신청과 직렬화한다(D-20, D-24). */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SessionResponse updateSession(Long sessionId, SessionRequest request) {
    CourseSession session =
        courseSessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    int oldCapacity = session.getCapacity();
    session.update(request.startAt(), request.place(), request.capacity(), now());
    int newCapacity = session.getCapacity();
    if (newCapacity != oldCapacity) {
      // 감소 시 강등(D-03)과 증가 시 승급(D-02)을 모두 핸들러가 처리한다.
      capacityChangeHandler.onCapacityChanged(session, oldCapacity, newCapacity);
    }
    return toSessionResponse(session);
  }

  /** 회차 락을 첫 쿼리로 잡아 신청과 직렬화한다(D-20, D-24). */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SessionResponse closeSession(Long sessionId) {
    CourseSession session =
        courseSessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    session.close(now());
    return toSessionResponse(session);
  }

  private Course findCourse(Long courseId) {
    return courseRepository
        .findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
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
