package com.example.eduhub.enrollment;

import java.time.Instant;

/** 신청 응답. {@code waitingNumber}는 WAITING일 때만 값이 있고 CONFIRMED면 null이다(D-23). */
public record EnrollmentResponse(
    Long id,
    Long sessionId,
    String employeeId,
    EnrollmentStatus status,
    Integer waitingNumber,
    Instant appliedAt) {

  public static EnrollmentResponse of(Enrollment enrollment, Integer waitingNumber) {
    return new EnrollmentResponse(
        enrollment.getId(),
        enrollment.getSession().getId(),
        enrollment.getEmployeeId(),
        enrollment.getStatus(),
        waitingNumber,
        enrollment.getAppliedAt());
  }
}
