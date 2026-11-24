package com.example.eduhub.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotificationRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-11-24T01:00:00Z");

  @Autowired private NotificationRepository repository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void savesAndReadsBackAllFields() {
    Notification saved =
        repository.saveAndFlush(
            new Notification(
                "E1001",
                NotificationType.WAITING_PROMOTED,
                NotificationType.WAITING_PROMOTED.messageFor(5L),
                CREATED_AT));
    entityManager.clear();

    Notification found = repository.findById(saved.getId()).orElseThrow();

    assertThat(found.getEmployeeId()).isEqualTo("E1001");
    assertThat(found.getType()).isEqualTo(NotificationType.WAITING_PROMOTED);
    assertThat(found.getMessage()).isEqualTo("회차 5 대기에서 수강 확정으로 승급되었습니다.");
    assertThat(found.getCreatedAt()).isEqualTo(CREATED_AT);

    Object storedType =
        entityManager
            .getEntityManager()
            .createNativeQuery("SELECT type FROM notification WHERE id = ?1")
            .setParameter(1, saved.getId())
            .getSingleResult();
    assertThat(storedType).isEqualTo("WAITING_PROMOTED");
  }

  @Test
  void findsOnlyGivenEmployeeInIdOrder() {
    Notification first =
        repository.save(notification("NR-A", NotificationType.ENROLLMENT_CONFIRMED));
    repository.save(notification("NR-B", NotificationType.ENROLLMENT_CONFIRMED));
    Notification third = repository.save(notification("NR-A", NotificationType.WAITING_PROMOTED));
    repository.flush();
    entityManager.clear();

    List<Notification> result = repository.findByEmployeeIdOrderByIdAsc("NR-A");

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(first.getId(), third.getId());
    assertThat(result).extracting(Notification::getEmployeeId).containsOnly("NR-A");
  }

  private static Notification notification(String employeeId, NotificationType type) {
    return new Notification(employeeId, type, type.messageFor(1L), CREATED_AT);
  }
}
