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
    EnrollmentStatus status =
        confirmed < session.getCapacity() && waiting == 0
            ? EnrollmentStatus.CONFIRMED
            : EnrollmentStatus.WAITING;
    // TODO(3주차, FR-05): WAITING 저장 시 대기 순번을 부여한다.
    Enrollment saved = save(new Enrollment(session, employeeId, status, now), sessionId);
    return EnrollmentResponse.from(saved);
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
