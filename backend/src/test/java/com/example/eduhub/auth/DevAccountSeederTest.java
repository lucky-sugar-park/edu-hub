package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 시더의 사번은 고정이라 다른 테스트·dev 데이터와 겹칠 수 있다. 롤백되는 테스트 트랜잭션 안에서 먼저 지우고 시작하므로 DB에는 아무것도 남지 않는다(시더의 트랜잭션은
 * 테스트 트랜잭션에 합류한다).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DevAccountSeederTest {

  private static final Instant NOW = Instant.parse("2026-11-25T01:00:00Z");
  private static final String SEED_NOS = "('E1001', 'E1002', 'A0001')";

  @Autowired private EmployeeRepository employeeRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestEntityManager entityManager;

  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  @BeforeEach
  void removeSeedAccounts() {
    jdbcTemplate.update("DELETE FROM employee WHERE employee_no IN " + SEED_NOS);
    entityManager.clear();
  }

  private DevAccountSeeder seeder(String password) {
    return new DevAccountSeeder(
        employeeRepository,
        encoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        transactionManager,
        password);
  }

  private int count() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM employee WHERE employee_no IN " + SEED_NOS, Integer.class);
  }

  private Employee find(String employeeNo) {
    return employeeRepository.findByEmployeeNo(employeeNo).orElseThrow();
  }

  @Test
  void createsThreeAccountsWithRolesAndMatchingPassword() {
    seeder("dev-pass").run(null);
    entityManager.clear();

    assertThat(count()).isEqualTo(3);
    for (String no : new String[] {"E1001", "E1002", "A0001"}) {
      assertThat(encoder.matches("dev-pass", find(no).getPasswordHash())).isTrue();
      assertThat(find(no).getCreatedAt()).isEqualTo(NOW);
    }
    assertThat(find("E1001").getRole()).isEqualTo(EmployeeRole.EMPLOYEE);
    assertThat(find("E1002").getRole()).isEqualTo(EmployeeRole.EMPLOYEE);
    assertThat(find("A0001").getRole()).isEqualTo(EmployeeRole.ADMIN);
  }

  @Test
  void createsNothingWhenPasswordIsEmpty() {
    seeder("").run(null);
    seeder("   ").run(null);

    assertThat(count()).isZero();
  }

  @Test
  void runningTwiceKeepsThreeAccounts() {
    seeder("dev-pass").run(null);
    entityManager.clear();
    String hashBefore = find("E1001").getPasswordHash();

    seeder("other-pass").run(null);
    entityManager.clear();

    assertThat(count()).isEqualTo(3);
    assertThat(find("E1001").getPasswordHash()).isEqualTo(hashBefore);
  }

  @Test
  void createsOnlyMissingAccounts() {
    employeeRepository.saveAndFlush(new Employee("E1002", "{bcrypt}x", EmployeeRole.EMPLOYEE, NOW));

    seeder("dev-pass").run(null);
    entityManager.clear();

    assertThat(count()).isEqualTo(3);
    assertThat(find("E1002").getPasswordHash()).isEqualTo("{bcrypt}x");
    assertThat(encoder.matches("dev-pass", find("E1001").getPasswordHash())).isTrue();
    assertThat(encoder.matches("dev-pass", find("A0001").getPasswordHash())).isTrue();
  }

  @Test
  void createsNothingWhenPasswordExceeds72BytesWithoutThrowing() {
    String tooLong = "a".repeat(73);
    String koreanTooLong = "가".repeat(25);

    assertThatCode(() -> seeder(tooLong).run(null)).doesNotThrowAnyException();
    assertThatCode(() -> seeder(koreanTooLong).run(null)).doesNotThrowAnyException();

    assertThat(count()).isZero();
  }

  @Test
  void accepts72BytePassword() {
    seeder("a".repeat(72)).run(null);

    assertThat(count()).isEqualTo(3);
  }
}
