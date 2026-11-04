package com.example.eduhub.course;

public record CourseSummaryResponse(Long id, String title, String category, CourseStatus status) {

  public static CourseSummaryResponse from(Course course) {
    return new CourseSummaryResponse(
        course.getId(), course.getTitle(), course.getCategory(), course.getStatus());
  }
}
