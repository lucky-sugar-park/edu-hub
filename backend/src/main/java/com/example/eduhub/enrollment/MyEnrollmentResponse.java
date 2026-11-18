package com.example.eduhub.enrollment;

import java.time.Instant;

/** 내 신청 내역 한 건. {@code waitingNumber}는 WAITING일 때만 값이 있다(D-23). */
public record MyEnrollmentResponse(
    Long id,
    String courseTitle,
    Long sessionId,
    Instant sessionStartAt,
    String sessionPlace,
    EnrollmentStatus status,
    Integer waitingNumber,
    Instant appliedAt) {}
