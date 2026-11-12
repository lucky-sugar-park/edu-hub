package com.example.eduhub.enrollment;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 대기 순번 계산과 활성 상태 상수를 한곳에 둔다(D-23). */
@Component
class WaitingNumbers {

  static final Set<EnrollmentStatus> ACTIVE =
      EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING);

  private final EnrollmentRepository enrollmentRepository;

  WaitingNumbers(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  /** 대기면 "같은 회차에서 키가 더 작은 대기자 수 + 1", 아니면 null. */
  Integer numberOf(Enrollment enrollment) {
    if (enrollment.getStatus() != EnrollmentStatus.WAITING) {
      return null;
    }
    long ahead =
        enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
            enrollment.getSession().getId(), EnrollmentStatus.WAITING, enrollment.getWaitingKey());
    return Math.toIntExact(ahead + 1);
  }
}
