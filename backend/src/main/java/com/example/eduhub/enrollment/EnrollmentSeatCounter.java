package com.example.eduhub.enrollment;

import com.example.eduhub.course.SessionSeatCounter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentSeatCounter implements SessionSeatCounter {

  /** 자리를 차지하는 상태. 수료자도 자리를 비우지 않는다(D-28). */
  private static final Set<EnrollmentStatus> SEAT_STATUSES =
      EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.COMPLETED);

  private final EnrollmentRepository enrollmentRepository;

  public EnrollmentSeatCounter(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  @Override
  public Map<Long, Long> countConfirmedBySessionIds(Collection<Long> sessionIds) {
    if (sessionIds.isEmpty()) {
      return Map.of();
    }
    return enrollmentRepository.countBySessionIdsAndStatusIn(sessionIds, SEAT_STATUSES).stream()
        .collect(
            Collectors.toMap(
                SessionConfirmedCount::getSessionId, SessionConfirmedCount::getConfirmed));
  }
}
