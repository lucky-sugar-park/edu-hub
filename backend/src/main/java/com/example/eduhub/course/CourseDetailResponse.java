package com.example.eduhub.course;

import java.util.List;
import java.util.Map;

public record CourseDetailResponse(
    Long id,
    String title,
    String category,
    String description,
    CourseStatus status,
    List<SessionResponse> sessions) {

  public static CourseDetailResponse of(
      Course course, List<CourseSession> sessions, Map<Long, Long> confirmedBySessionId) {
    return new CourseDetailResponse(
        course.getId(),
        course.getTitle(),
        course.getCategory(),
        course.getDescription(),
        course.getStatus(),
        sessions.stream()
            .map(s -> SessionResponse.of(s, confirmedBySessionId.getOrDefault(s.getId(), 0L)))
            .toList());
  }
}
