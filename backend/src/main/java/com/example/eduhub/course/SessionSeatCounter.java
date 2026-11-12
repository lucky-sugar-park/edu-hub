package com.example.eduhub.course;

import java.util.Collection;
import java.util.Map;

/** 회차별 확정 신청 수 조회. {@code course}가 {@code enrollment}에 의존하지 않도록 둔 인터페이스다(D-22). */
public interface SessionSeatCounter {

  /** 회차별 확정 신청 수. 결과에 없는 회차는 0명이다. */
  Map<Long, Long> countConfirmedBySessionIds(Collection<Long> sessionIds);
}
