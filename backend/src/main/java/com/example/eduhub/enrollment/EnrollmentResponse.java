package com.example.eduhub.enrollment;

import java.time.Instant;

public record EnrollmentResponse(
    Long id, Long sessionId, String employeeId, EnrollmentStatus status, Instant appliedAt) {

  public static EnrollmentResponse from(Enrollment enrollment) {
    return new EnrollmentResponse(
        enrollment.getId(),
        enrollment.getSession().getId(),
        enrollment.getEmployeeId(),
        enrollment.getStatus(),
        enrollment.getAppliedAt());
  }
}
