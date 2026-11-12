package com.example.eduhub.enrollment;

import com.example.eduhub.common.ConflictException;

/** 같은 회차에 활성 신청(확정, 대기)이 이미 있다. */
public class DuplicateEnrollmentException extends ConflictException {

  public DuplicateEnrollmentException(Long sessionId) {
    super("DUPLICATE_ENROLLMENT", "이미 신청한 회차입니다: " + sessionId);
  }
}
