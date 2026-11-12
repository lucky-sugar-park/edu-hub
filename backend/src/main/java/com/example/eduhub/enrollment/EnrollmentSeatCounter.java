package com.example.eduhub.enrollment;

import com.example.eduhub.course.SessionSeatCounter;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentSeatCounter implements SessionSeatCounter {

  private final EnrollmentRepository enrollmentRepository;

  public EnrollmentSeatCounter(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  @Override
  public Map<Long, Long> countConfirmedBySessionIds(Collection<Long> sessionIds) {
    if (sessionIds.isEmpty()) {
      return Map.of();
    }
    return enrollmentRepository
        .countBySessionIdsAndStatus(sessionIds, EnrollmentStatus.CONFIRMED)
        .stream()
        .collect(
            Collectors.toMap(
                SessionConfirmedCount::getSessionId, SessionConfirmedCount::getConfirmed));
  }
}
