package com.example.eduhub.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationTypeTest {

  @Test
  void enrollmentConfirmedMessageContainsSessionId() {
    assertThat(NotificationType.ENROLLMENT_CONFIRMED.messageFor(7L))
        .isEqualTo("회차 7 수강 신청이 확정되었습니다.");
  }

  @Test
  void waitingPromotedMessageContainsSessionId() {
    assertThat(NotificationType.WAITING_PROMOTED.messageFor(7L))
        .isEqualTo("회차 7 대기에서 수강 확정으로 승급되었습니다.");
  }
}
