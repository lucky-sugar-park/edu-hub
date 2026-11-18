package com.example.eduhub.enrollment;

import com.example.eduhub.common.PageResponse;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신청 조회(FR-05, FR-07). 락 없이 조회 시점의 순번을 계산한다. */
@Service
@Transactional(readOnly = true)
public class EnrollmentQueryService {

  private static final Set<EnrollmentStatus> ROSTER_STATUSES =
      EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.COMPLETED, EnrollmentStatus.WAITING);

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

  /**
   * 회차 명단(FR-08). 쿼리는 회차 1 + 신청 1이다. 확정·수료는 확정 시각, id 오름차순이고 대기는 키 오름차순에 순번 1..n이다. 정렬은 서비스에서 한다.
   */
  public SessionRosterResponse getRoster(Long sessionId) {
    CourseSession session =
        courseSessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    List<Enrollment> rows =
        enrollmentRepository.findBySessionIdAndStatusIn(sessionId, ROSTER_STATUSES);

    // 확정 시각은 확정·수료 행에 항상 있지만, 데이터 이상에도 NPE가 나지 않도록 null은 뒤로 보낸다.
    List<SessionRosterResponse.ConfirmedEntry> confirmed =
        rows.stream()
            .filter(e -> e.getStatus() != EnrollmentStatus.WAITING)
            .sorted(
                Comparator.comparing(
                        Enrollment::getConfirmedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Enrollment::getId))
            .map(e ->
                new SessionRosterResponse.ConfirmedEntry(
                    e.getId(),
                    e.getEmployeeId(),
                    e.getStatus(),
                    e.getConfirmedAt(),
                    e.getCompletedAt()))
            .toList();

    List<Enrollment> waitingRows =
        rows.stream()
            .filter(e -> e.getStatus() == EnrollmentStatus.WAITING)
            // 대기 행의 키는 항상 있지만(D-23), 확정 쪽과 같이 방어용으로 null을 뒤로 보내 순서를 결정적으로 둔다.
            .sorted(
                Comparator.comparing(
                        Enrollment::getWaitingKey, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Enrollment::getId))
            .toList();
    List<SessionRosterResponse.WaitingEntry> waiting = new ArrayList<>(waitingRows.size());
    for (int i = 0; i < waitingRows.size(); i++) {
      Enrollment e = waitingRows.get(i);
      waiting.add(
          new SessionRosterResponse.WaitingEntry(
              e.getId(), e.getEmployeeId(), i + 1, e.getAppliedAt()));
    }
    return new SessionRosterResponse(
        session.getId(), session.getCapacity(), session.getStatus(), confirmed, waiting);
  }

  /** 내 신청 내역. 정렬은 신청 일시 내림차순으로 고정하고 호출자의 sort는 버린다(FR-07). */
  public PageResponse<MyEnrollmentResponse> getMyEnrollments(
      String employeeId, EnrollmentStatus status, Pageable pageable) {
    Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return PageResponse.from(
        enrollmentRepository
            .findMyEnrollments(employeeId, status, unsorted)
            .map(MyEnrollmentRow::toResponse));
  }
}
