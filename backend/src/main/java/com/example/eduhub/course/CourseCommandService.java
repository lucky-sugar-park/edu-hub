package com.example.eduhub.course;

import com.example.eduhub.common.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 과정·회차 등록, 수정, 종료(FR-03). 조회는 {@link CourseQueryService}가 맡는다. */
@Service
@Transactional
public class CourseCommandService {

  private final CourseRepository courseRepository;
  private final CourseSessionRepository courseSessionRepository;
  private final Clock clock;

  public CourseCommandService(
      CourseRepository courseRepository,
      CourseSessionRepository courseSessionRepository,
      Clock clock) {
    this.courseRepository = courseRepository;
    this.courseSessionRepository = courseSessionRepository;
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
    return SessionResponse.from(session);
  }

  public SessionResponse updateSession(Long sessionId, SessionRequest request) {
    CourseSession session = findSession(sessionId);
    session.update(request.startAt(), request.place(), request.capacity(), now());
    // TODO(3주차, FR-05): 정원이 바뀌면 D-02(대기자 승급), D-03(확정자 강등)을 여기서 이어 처리한다.
    return SessionResponse.from(session);
  }

  public SessionResponse closeSession(Long sessionId) {
    CourseSession session = findSession(sessionId);
    session.close(now());
    return SessionResponse.from(session);
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
    return CourseDetailResponse.of(
        course, courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(course.getId()));
  }

  private Instant now() {
    return clock.instant();
  }
}
