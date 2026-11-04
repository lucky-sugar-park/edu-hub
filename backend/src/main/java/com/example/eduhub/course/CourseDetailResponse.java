package com.example.eduhub.course;

import java.util.List;

public record CourseDetailResponse(
    Long id,
    String title,
    String category,
    String description,
    CourseStatus status,
    List<SessionResponse> sessions) {

  public static CourseDetailResponse of(Course course, List<CourseSession> sessions) {
    return new CourseDetailResponse(
        course.getId(),
        course.getTitle(),
        course.getCategory(),
        course.getDescription(),
        course.getStatus(),
        sessions.stream().map(SessionResponse::from).toList());
  }
}
