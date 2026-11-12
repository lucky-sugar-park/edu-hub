package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신청 조회(FR-05). 락 없이 조회 시점의 순번을 계산한다. */
@Service
@Transactional(readOnly = true)
public class EnrollmentQueryService {

  private static final Set<EnrollmentStatus> ACTIVE =
      EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING);

  private final CourseSessionRepository courseSessionRepository;
  private final EnrollmentRepository enrollmentRepository;

  public EnrollmentQueryService(
      CourseSessionRepository courseSessionRepository, EnrollmentRepository enrollmentRepository) {
    this.courseSessionRepository = courseSessionRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  /** 회차에서 내 활성 신청과 현재 대기 순번을 돌려준다. */
  public EnrollmentResponse getMyEnrollment(Long sessionId, String employeeId) {
    if (!courseSessionRepository.existsById(sessionId)) {
      throw new SessionNotFoundException(sessionId);
    }
    Enrollment enrollment =
        enrollmentRepository
            .findBySessionIdAndEmployeeIdAndStatusIn(sessionId, employeeId, ACTIVE)
            .orElseThrow(() -> new EnrollmentNotFoundException(sessionId));
    Integer waitingNumber = null;
    if (enrollment.getStatus() == EnrollmentStatus.WAITING) {
      long ahead =
          enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
              sessionId, EnrollmentStatus.WAITING, enrollment.getWaitingKey());
      waitingNumber = Math.toIntExact(ahead + 1);
    }
    return EnrollmentResponse.of(enrollment, waitingNumber);
  }
}
