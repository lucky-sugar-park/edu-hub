package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 수강 신청(FR-04). 회차 행을 비관적 락으로 잡아 같은 회차의 신청을 직렬화한다(D-20). */
@Service
public class EnrollmentCommandService {

  private static final Set<EnrollmentStatus> ACTIVE =
      EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING);
  private static final String ACTIVE_UNIQUE_CONSTRAINT = "uk_enrollment_active";

  private final CourseSessionRepository courseSessionRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final Clock clock;

  public EnrollmentCommandService(
      CourseSessionRepository courseSessionRepository,
      EnrollmentRepository enrollmentRepository,
      Clock clock) {
    this.courseSessionRepository = courseSessionRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.clock = clock;
  }

  /**
   * 회차에 신청한다. 정원 안이면 확정, 찼으면 대기로 저장한다.
   *
   * <p>회차 락 조회가 트랜잭션의 첫 쿼리여야 한다. READ_COMMITTED로 두어 락을 얻은 뒤의 확정 수 조회가 앞 트랜잭션의 커밋을 항상 보게 한다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public EnrollmentResponse enroll(Long sessionId, String employeeId) {
    CourseSession session =
        courseSessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    Instant now = clock.instant();
    session.ensureEnrollable(now);
    session.getCourse().ensureOpen();
    if (enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(
        sessionId, employeeId, ACTIVE)) {
      throw new DuplicateEnrollmentException(sessionId);
    }
    long confirmed =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    long waiting =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.WAITING);
    // D-21: 정원이 남아도 대기자가 있으면 앞지르지 못하고 대기한다.
    Enrollment enrollment;
    if (confirmed < session.getCapacity() && waiting == 0) {
      enrollment = Enrollment.confirmed(session, employeeId, now);
    } else {
      // D-23: 새 대기자는 회차 락 안에서 키 최댓값 + 1을 받는다(D-05).
      long waitingKey = enrollmentRepository.findMaxWaitingKey(sessionId).orElse(0L) + 1;
      enrollment = Enrollment.waiting(session, employeeId, now, waitingKey);
    }
    Enrollment saved = save(enrollment, sessionId);
    return EnrollmentResponse.of(saved, waitingNumberOf(saved, sessionId));
  }

  /** 회차 락 안에서 계산한다. 순번 = 나보다 앞선 대기자 수 + 1. 확정이면 null. */
  private Integer waitingNumberOf(Enrollment saved, Long sessionId) {
    if (saved.getStatus() != EnrollmentStatus.WAITING) {
      return null;
    }
    long ahead =
        enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
            sessionId, EnrollmentStatus.WAITING, saved.getWaitingKey());
    return Math.toIntExact(ahead + 1);
  }

  private Enrollment save(Enrollment enrollment, Long sessionId) {
    try {
      return enrollmentRepository.saveAndFlush(enrollment);
    } catch (DataIntegrityViolationException exception) {
      if (violatesActiveUnique(exception)) {
        throw new DuplicateEnrollmentException(sessionId);
      }
      throw exception;
    }
  }

  private boolean violatesActiveUnique(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      String message = cause.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains(ACTIVE_UNIQUE_CONSTRAINT)) {
        return true;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return false;
  }
}
