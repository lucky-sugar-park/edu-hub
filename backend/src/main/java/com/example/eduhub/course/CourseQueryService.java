package com.example.eduhub.course;

import com.example.eduhub.common.PageResponse;
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

  public CourseQueryService(
      CourseRepository courseRepository, CourseSessionRepository courseSessionRepository) {
    this.courseRepository = courseRepository;
    this.courseSessionRepository = courseSessionRepository;
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
    return CourseDetailResponse.of(
        course, courseSessionRepository.findByCourseIdOrderByStartAtAscIdAsc(id));
  }
}
