package com.example.eduhub.course;

import java.time.Instant;

public record SessionResponse(
    Long id,
    Instant startAt,
    String place,
    int capacity,
    int remainingSeats,
    CourseSessionStatus status) {

  public static SessionResponse from(CourseSession session) {
    // 수강 신청 구현 전까지 남은 자리는 정원과 같다.
    return new SessionResponse(
        session.getId(),
        session.getStartAt(),
        session.getPlace(),
        session.getCapacity(),
        session.getCapacity(),
        session.getStatus());
  }
}
