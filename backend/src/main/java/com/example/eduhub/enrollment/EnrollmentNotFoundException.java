package com.example.eduhub.enrollment;

import com.example.eduhub.common.NotFoundException;

public class EnrollmentNotFoundException extends NotFoundException {

  public EnrollmentNotFoundException(Long sessionId) {
    super("신청 내역이 없습니다: " + sessionId);
  }
}
