package com.example.eduhub.enrollment;

/** 회차별 확정 신청 수 집계 결과. */
public interface SessionConfirmedCount {

  Long getSessionId();

  long getConfirmed();
}
