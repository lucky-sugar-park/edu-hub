package com.example.eduhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eduhub.enrollment.EnrollmentConfirmedEvent;
import com.example.eduhub.enrollment.EnrollmentConfirmedEvent.Reason;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

  private static final Instant NOW = Instant.parse("2026-11-24T01:02:03Z");

  @Mock NotificationRepository repository;

  NotificationListener listener;

  @BeforeEach
  void setUp() {
    listener = new NotificationListener(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void APPLIED는_신청_확정_알림으로_저장한다() {
    listener.on(new EnrollmentConfirmedEvent(10L, "E001", 7L, Reason.APPLIED));

    Notification saved = captureSaved();
    assertThat(saved.getEmployeeId()).isEqualTo("E001");
    assertThat(saved.getType()).isEqualTo(NotificationType.ENROLLMENT_CONFIRMED);
    assertThat(saved.getMessage()).isEqualTo("회차 7 수강 신청이 확정되었습니다.");
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void PROMOTED는_승급_알림으로_저장한다() {
    listener.on(new EnrollmentConfirmedEvent(11L, "E002", 8L, Reason.PROMOTED));

    Notification saved = captureSaved();
    assertThat(saved.getEmployeeId()).isEqualTo("E002");
    assertThat(saved.getType()).isEqualTo(NotificationType.WAITING_PROMOTED);
    assertThat(saved.getMessage()).isEqualTo("회차 8 대기에서 수강 확정으로 승급되었습니다.");
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void 저장이_실패하면_같은_예외가_전파된다() {
    IllegalStateException failure = new IllegalStateException("db down");
    when(repository.save(any(Notification.class))).thenThrow(failure);

    assertThatThrownBy(
            () -> listener.on(new EnrollmentConfirmedEvent(12L, "E003", 9L, Reason.APPLIED)))
        .isSameAs(failure);
  }

  private Notification captureSaved() {
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }
}
