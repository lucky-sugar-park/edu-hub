package com.example.eduhub.enrollment;

/**
 * 신청이 확정되었음을 알리는 이벤트(FR-09). 즉시 확정(APPLIED)과 대기 승급(PROMOTED) 두 경우에 발행한다.
 *
 * <p>트랜잭션 안에서 발행되며 구독자는 커밋 직전에 같은 트랜잭션에서 받는다(D-32). {@code enrollment}는 구독자를 알지 못한다.
 */
public record EnrollmentConfirmedEvent(
    Long enrollmentId, String employeeId, Long sessionId, Reason reason) {

  public enum Reason {
    APPLIED,
    PROMOTED
  }

  static EnrollmentConfirmedEvent applied(Enrollment enrollment) {
    return of(enrollment, Reason.APPLIED);
  }

  static EnrollmentConfirmedEvent promoted(Enrollment enrollment) {
    return of(enrollment, Reason.PROMOTED);
  }

  private static EnrollmentConfirmedEvent of(Enrollment enrollment, Reason reason) {
    return new EnrollmentConfirmedEvent(
        enrollment.getId(), enrollment.getEmployeeId(), enrollment.getSession().getId(), reason);
  }
}
