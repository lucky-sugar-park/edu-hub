package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 수료 처리(FR-08). 종료된 회차에서 회차 행 락을 잡고 확정자를 수료로 바꾼다(D-28). 대기자는 바꾸지 않는다(D-09, D-10). */
@Service
public class CompletionService {

  private final CourseSessionRepository courseSessionRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final Clock clock;

  public CompletionService(
      CourseSessionRepository courseSessionRepository,
      EnrollmentRepository enrollmentRepository,
      Clock clock) {
    this.courseSessionRepository = courseSessionRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.clock = clock;
  }

  /**
   * 사번 목록을 수료 처리한다. 일부만 실패해도 성공한 건은 한 트랜잭션에서 반영하고, 실패 건은 상태를 바꾸지 않는다.
   *
   * <p>회차 락 조회가 트랜잭션의 첫 쿼리여야 한다. 응답은 요청 순서를 따른다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CompletionResponse complete(Long sessionId, List<String> employeeIds) {
    CourseSession session =
        courseSessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    Instant now = clock.instant();
    session.ensureCompletable(now);

    LinkedHashSet<String> requested = new LinkedHashSet<>(employeeIds);
    Map<String, List<Enrollment>> byEmployee = new HashMap<>();
    for (Enrollment enrollment :
        enrollmentRepository.findBySessionIdAndEmployeeIdIn(sessionId, requested)) {
      byEmployee
          .computeIfAbsent(enrollment.getEmployeeId(), k -> new ArrayList<>())
          .add(enrollment);
    }

    List<String> completed = new ArrayList<>();
    List<CompletionResponse.Failure> failed = new ArrayList<>();
    List<Enrollment> changed = new ArrayList<>();
    for (String employeeId : requested) {
      List<Enrollment> rows = byEmployee.getOrDefault(employeeId, List.of());
      Enrollment active = first(rows, EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING);
      if (active != null && active.getStatus() == EnrollmentStatus.CONFIRMED) {
        active.complete(now);
        changed.add(active);
        completed.add(employeeId);
      } else if (active != null) {
        failed.add(failure(employeeId, CompletionFailureReason.NOT_CONFIRMED));
      } else if (first(rows, EnrollmentStatus.COMPLETED) != null) {
        failed.add(failure(employeeId, CompletionFailureReason.ALREADY_COMPLETED));
      } else if (first(rows, EnrollmentStatus.CANCELLED) != null) {
        failed.add(failure(employeeId, CompletionFailureReason.CANCELLED));
      } else {
        failed.add(failure(employeeId, CompletionFailureReason.NOT_ENROLLED));
      }
    }
    enrollmentRepository.saveAllAndFlush(changed);
    return new CompletionResponse(completed, failed);
  }

  private static Enrollment first(Collection<Enrollment> rows, EnrollmentStatus... statuses) {
    for (Enrollment row : rows) {
      for (EnrollmentStatus status : statuses) {
        if (row.getStatus() == status) {
          return row;
        }
      }
    }
    return null;
  }

  private static CompletionResponse.Failure failure(
      String employeeId, CompletionFailureReason reason) {
    return new CompletionResponse.Failure(employeeId, reason);
  }
}
