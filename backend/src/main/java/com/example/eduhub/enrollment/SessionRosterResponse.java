package com.example.eduhub.enrollment;

import com.example.eduhub.course.CourseSessionStatus;
import java.time.Instant;
import java.util.List;

/**
 * 회차 명단(FR-08). 확정·수료자와 대기자를 나눠 담는다. 취소자는 넣지 않는다. 과정·회차 정보({@code courseId}, {@code courseTitle},
 * {@code startAt}, {@code place})는 명단 화면을 주소로 바로 열어도 회차를 알아볼 수 있게 한다.
 */
public record SessionRosterResponse(
    Long sessionId,
    Long courseId,
    String courseTitle,
    Instant startAt,
    String place,
    int capacity,
    CourseSessionStatus sessionStatus,
    List<ConfirmedEntry> confirmed,
    List<WaitingEntry> waiting) {

  /** 확정·수료자. {@code completedAt}은 수료자만 값이 있다. */
  public record ConfirmedEntry(
      Long enrollmentId,
      String employeeId,
      EnrollmentStatus status,
      Instant confirmedAt,
      Instant completedAt) {}

  /** 대기자. {@code waitingNumber}는 목록 순서의 1부터 매긴 값이다(D-23과 같다). */
  public record WaitingEntry(
      Long enrollmentId, String employeeId, int waitingNumber, Instant appliedAt) {}
}
