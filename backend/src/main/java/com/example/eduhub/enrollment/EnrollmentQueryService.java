package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신청 조회(FR-05). 락 없이 조회 시점의 순번을 계산한다. */
@Service
@Transactional(readOnly = true)
public class EnrollmentQueryService {

  private final CourseSessionRepository courseSessionRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final WaitingNumbers waitingNumbers;

  public EnrollmentQueryService(
      CourseSessionRepository courseSessionRepository,
      EnrollmentRepository enrollmentRepository,
      WaitingNumbers waitingNumbers) {
    this.courseSessionRepository = courseSessionRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.waitingNumbers = waitingNumbers;
  }

  /** 회차에서 내 활성 신청과 현재 대기 순번을 돌려준다. */
  public EnrollmentResponse getMyEnrollment(Long sessionId, String employeeId) {
    if (!courseSessionRepository.existsById(sessionId)) {
      throw new SessionNotFoundException(sessionId);
    }
    Enrollment enrollment =
        enrollmentRepository
            .findBySessionIdAndEmployeeIdAndStatusIn(sessionId, employeeId, WaitingNumbers.ACTIVE)
            .orElseThrow(() -> new EnrollmentNotFoundException(sessionId));
    return EnrollmentResponse.of(enrollment, waitingNumbers.numberOf(enrollment));
  }
}
