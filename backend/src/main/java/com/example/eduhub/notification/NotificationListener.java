package com.example.eduhub.notification;

import com.example.eduhub.enrollment.EnrollmentConfirmedEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신청 확정·승급 이벤트를 받아 알림을 기록한다(FR-09, D-32).
 *
 * <p>신청 트랜잭션이 커밋되기 직전(BEFORE_COMMIT)에 같은 트랜잭션에서 저장하므로 추가 커넥션을 쓰지 않는다. 저장이 실패하면 예외가 전파되어 신청과 함께
 * 롤백된다.
 */
@Component
class NotificationListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

  private final NotificationRepository repository;
  private final Clock clock;

  NotificationListener(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(EnrollmentConfirmedEvent event) {
    NotificationType type =
        switch (event.reason()) {
          case APPLIED -> NotificationType.ENROLLMENT_CONFIRMED;
          case PROMOTED -> NotificationType.WAITING_PROMOTED;
        };
    repository.save(
        new Notification(
            event.employeeId(), type, type.messageFor(event.sessionId()), clock.instant()));
    // BEFORE_COMMIT이라 이 로그 뒤에 커밋이 실패하면 알림도 롤백된다(로그만 남을 수 있다).
    log.info(
        "알림 기록(커밋 전): employeeId={}, type={}, enrollmentId={}, sessionId={}",
        event.employeeId(),
        type,
        event.enrollmentId(),
        event.sessionId());
  }
}
