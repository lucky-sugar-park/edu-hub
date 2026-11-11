package com.example.eduhub.course;

import com.example.eduhub.common.NotFoundException;

public class SessionNotFoundException extends NotFoundException {

  public SessionNotFoundException(Long id) {
    super("회차를 찾을 수 없습니다: " + id);
  }
}
