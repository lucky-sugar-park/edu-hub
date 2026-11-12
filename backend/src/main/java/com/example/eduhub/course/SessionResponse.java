package com.example.eduhub.course;

import java.time.Instant;

public record SessionResponse(
    Long id,
    Instant startAt,
    String place,
    int capacity,
    int remainingSeats,
    CourseSessionStatus status) {

  /** 남은 자리는 {@code 정원 - 확정 수}이며 0 미만이 되지 않는다(D-22). */
  public static SessionResponse of(CourseSession session, long confirmed) {
    return new SessionResponse(
        session.getId(),
        session.getStartAt(),
        session.getPlace(),
        session.getCapacity(),
        (int) Math.max(0, session.getCapacity() - confirmed),
        session.getStatus());
  }
}
