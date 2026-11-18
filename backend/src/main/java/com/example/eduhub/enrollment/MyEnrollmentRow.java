package com.example.eduhub.enrollment;

import java.time.Instant;

/** 조회 쿼리의 결과 행. 서브쿼리 count가 Long이라 순번만 Long으로 받는다. */
record MyEnrollmentRow(
    Long id,
    Long courseId,
    String courseTitle,
    Long sessionId,
    Instant sessionStartAt,
    String sessionPlace,
    EnrollmentStatus status,
    Long waitingNumber,
    Instant appliedAt) {

  MyEnrollmentResponse toResponse() {
    return new MyEnrollmentResponse(
        id,
        courseId,
        courseTitle,
        sessionId,
        sessionStartAt,
        sessionPlace,
        status,
        waitingNumber == null ? null : Math.toIntExact(waitingNumber),
        appliedAt);
  }
}
