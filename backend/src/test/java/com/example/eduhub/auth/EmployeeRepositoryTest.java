package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 각 테스트는 트랜잭션 안에서 돌고 끝나면 롤백되므로 DB에 데이터가 남지 않는다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmployeeRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-11-25T01:00:00Z");
  private static final String HASH =
      "{bcrypt}$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234";

  @Autowired private EmployeeRepository employeeRepository;

  @Autowired private TestEntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void savesAndReadsByEmployeeNo() {
    Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Employee saved =
        employeeRepository.saveAndFlush(
            new Employee("T1-E001", HASH, EmployeeRole.ADMIN, createdAt));
    entityManager.clear();

    Employee found = employeeRepository.findByEmployeeNo("T1-E001").orElseThrow();

    assertThat(found).isNotSameAs(saved);
    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getEmployeeNo()).isEqualTo("T1-E001");
    assertThat(found.getPasswordHash()).isEqualTo(HASH);
    assertThat(found.getRole()).isEqualTo(EmployeeRole.ADMIN);
    assertThat(found.getCreatedAt()).isEqualTo(createdAt);
    assertThat(found.toString()).doesNotContain(HASH).doesNotContain("passwordHash");

    List<String> roles =
        jdbcTemplate.queryForList(
            "SELECT role FROM employee WHERE employee_no = ?", String.class, "T1-E001");
    assertThat(roles).containsExactly("ADMIN");

    // Hibernate는 Instant를 UTC 벽시계 시각으로 저장한다.
    LocalDateTime rawCreatedAt =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM employee WHERE employee_no = ?",
            LocalDateTime.class,
            "T1-E001");
    assertThat(rawCreatedAt.toInstant(ZoneOffset.UTC)).isEqualTo(createdAt);
  }

  @Test
  void rejectsUnknownRole() {
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO employee (employee_no, password_hash, role, created_at)"
                    + " VALUES (?, ?, ?, NOW(6))",
                "T1-E004",
                HASH,
                "X"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_employee_role");
  }

  @Test
  void existsByEmployeeNo() {
    employeeRepository.saveAndFlush(
        new Employee("T1-E002", HASH, EmployeeRole.EMPLOYEE, CREATED_AT));

    assertThat(employeeRepository.existsByEmployeeNo("T1-E002")).isTrue();
    assertThat(employeeRepository.existsByEmployeeNo("T1-NONE")).isFalse();
    assertThat(employeeRepository.findByEmployeeNo("T1-NONE")).isEmpty();
  }

  @Test
  void rejectsDuplicateEmployeeNo() {
    employeeRepository.saveAndFlush(
        new Employee("T1-E003", HASH, EmployeeRole.EMPLOYEE, CREATED_AT));

    assertThatThrownBy(() ->
            employeeRepository.saveAndFlush(
                new Employee("T1-E003", HASH, EmployeeRole.ADMIN, CREATED_AT)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_employee_no");
  }
}
