package com.example.eduhub.course;

import com.example.eduhub.common.NotFoundException;

public class CourseNotFoundException extends NotFoundException {

  public CourseNotFoundException(Long id) {
    super("과정을 찾을 수 없습니다: " + id);
  }
}
