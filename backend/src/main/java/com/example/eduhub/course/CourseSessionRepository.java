package com.example.eduhub.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

  List<CourseSession> findByCourseIdOrderByStartAtAscIdAsc(Long courseId);
}
