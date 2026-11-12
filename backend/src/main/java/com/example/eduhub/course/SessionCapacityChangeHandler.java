package com.example.eduhub.course;

/**
 * 회차 정원 변경에 따른 신청 처리. {@code course}가 {@code enrollment}에 의존하지 않도록 둔 인터페이스다(D-22).
 *
 * <p>구현체는 {@code enrollment}에 있다.
 */
public interface SessionCapacityChangeHandler {

  /** 회차 정원이 바뀐 뒤 같은 트랜잭션·회차 락 안에서 불린다. */
  void onCapacityChanged(CourseSession session, int oldCapacity, int newCapacity);
}
