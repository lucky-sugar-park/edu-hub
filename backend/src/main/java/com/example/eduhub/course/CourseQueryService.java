package com.example.eduhub.course;

import com.example.eduhub.common.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

  private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

  private final CourseRepository courseRepository;
  private final CourseSessionRepository courseSessionRepository;

  private final SessionSeatCounter sessionSeatCounter;

  public CourseQueryService(
      CourseRepository courseRepository,
      CourseSessionRepository courseSessionRepository,
      SessionSeatCounter sessionSeatCounter) {
    this.courseRepository = courseRepository;
    this.courseSessionRepository = courseSessionRepository;
    this.sessionSeatCounter = sessionSeatCounter;
  }

  public PageResponse<CourseSummaryResponse> searchCourses(
      String keyword, String category, Pageable pageable) {
    Pageable latestFirst =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
    return PageResponse.from(
        courseRepository.search(keyword, category, latestFirst).map(CourseSummaryResponse::from));
  }

  public CourseDetailResponse getCourse(Long id) {
    Course course =
        courseRepository.findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    List<CourseSession> sessions = courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(id);
    List<Long> sessionIds = sessions.stream().map(CourseSession::getId).toList();
    return CourseDetailResponse.of(
        course, sessions, sessionSeatCounter.countConfirmedBySessionIds(sessionIds));
  }
}
