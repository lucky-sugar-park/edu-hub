package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.SessionCapacityChangeHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 정원 감소 시 가장 늦게 확정된 사람부터 기존 대기자보다 앞선 대기로 내린다(D-03, D-24). */
@Component
public class CapacityChangeEnrollmentHandler implements SessionCapacityChangeHandler {

  private final EnrollmentRepository enrollmentRepository;

  public CapacityChangeEnrollmentHandler(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  @Override
  public void onCapacityChanged(CourseSession session, int oldCapacity, int newCapacity) {
    if (newCapacity > oldCapacity) {
      // TODO(FR-06, D-02): 정원이 늘면 대기자를 앞에서부터 승급한다.
      return;
    }
    if (newCapacity == oldCapacity) {
      return;
    }
    Long sessionId = session.getId();
    long confirmed =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    if (confirmed <= newCapacity) {
      return;
    }
    int excess = (int) (confirmed - newCapacity);
    // 가장 늦게 확정된 순(동률은 id 큰 순)으로 초과 인원을 고른다.
    List<Enrollment> targets =
        new ArrayList<>(
            enrollmentRepository.findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(
                sessionId, EnrollmentStatus.CONFIRMED, PageRequest.of(0, excess)));
    // 먼저 확정된 사람이 앞 순번이 되도록 뒤집는다.
    Collections.reverse(targets);
    // 기존 최솟값보다 작은 키만 부여하므로 기존 키와 충돌하지 않는다.
    long key = enrollmentRepository.findMinWaitingKey(sessionId).orElse(1L) - targets.size();
    for (Enrollment enrollment : targets) {
      enrollment.demote(key++);
    }
    enrollmentRepository.saveAllAndFlush(targets);
  }
}
