package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** DB 없이 시더의 예외 분기와 프로필 제한을 확인한다. */
class DevAccountSeederUnitTest {

  private final EmployeeRepository repository = mock(EmployeeRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(repository.existsByEmployeeNo(any())).thenReturn(false);
  }

  private DevAccountSeeder seeder() {
    return new DevAccountSeeder(
        repository,
        PasswordEncoderFactories.createDelegatingPasswordEncoder(),
        Clock.fixed(Instant.parse("2026-11-25T01:00:00Z"), ZoneOffset.UTC),
        transactionManager,
        "dev-pass");
  }

  @Test
  void uniqueConstraintViolationRollsBackAndStartupContinues() {
    doThrow(new DataIntegrityViolationException("uk_employee_no")).when(repository).flush();

    assertThatCode(() -> seeder().run(null)).doesNotThrowAnyException();

    verify(transactionManager).rollback(any(TransactionStatus.class));
    verify(transactionManager, never()).commit(any());
  }

  @Test
  void otherDataAccessExceptionsPropagate() {
    doThrow(new DataAccessResourceFailureException("db down")).when(repository).flush();

    assertThatThrownBy(() -> seeder().run(null))
        .isInstanceOf(DataAccessResourceFailureException.class);
    verify(transactionManager).rollback(any(TransactionStatus.class));
    verify(transactionManager, never()).commit(any());
  }

  @Test
  void isOnlyActiveInDevProfile() {
    Profile profile = DevAccountSeeder.class.getAnnotation(Profile.class);

    assertThat(profile).isNotNull();
    assertThat(profile.value()).containsExactly("dev");
  }
}
