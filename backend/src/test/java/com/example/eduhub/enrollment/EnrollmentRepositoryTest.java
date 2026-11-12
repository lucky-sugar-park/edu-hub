package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EnrollmentRepositoryTest {

  private static final Instant START_AT = Instant.parse("2026-12-01T01:00:00Z");
  private static final Instant APPLIED_AT = Instant.parse("2026-11-12T01:00:00Z");
  private static final String EMPLOYEE = "E1001";

  @Autowired private EnrollmentRepository enrollmentRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void rejectsDuplicateConfirmedEnrollmentInSameSession() {
    CourseSession session = saveSession(saveCourse(), 10);
    enrollmentRepository.saveAndFlush(enrollment(session, EMPLOYEE, EnrollmentStatus.CONFIRMED));

    assertThatThrownBy(() ->
            enrollmentRepository.saveAndFlush(
                enrollment(session, EMPLOYEE, EnrollmentStatus.CONFIRMED)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_enrollment_active");
  }

  @Test
  void rejectsConfirmedAndWaitingForSameEmployeeInSameSession() {
    CourseSession session = saveSession(saveCourse(), 10);
    enrollmentRepository.saveAndFlush(enrollment(session, EMPLOYEE, EnrollmentStatus.CONFIRMED));

    assertThatThrownBy(() ->
            enrollmentRepository.saveAndFlush(
                enrollment(session, EMPLOYEE, EnrollmentStatus.WAITING)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_enrollment_active");
  }

  @Test
  void allowsReEnrollmentWhenPreviousEnrollmentIsNotActive() {
    CourseSession session = saveSession(saveCourse(), 10);
    Enrollment first =
        enrollmentRepository.saveAndFlush(
            enrollment(session, EMPLOYEE, EnrollmentStatus.CONFIRMED));
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE enrollment SET status = 'CANCELLED' WHERE id = ?1")
        .setParameter(1, first.getId())
        .executeUpdate();
    entityManager.clear();

    Enrollment second =
        enrollmentRepository.saveAndFlush(
            enrollment(session, EMPLOYEE, EnrollmentStatus.CONFIRMED));

    assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
  }

  @Test
  void allowsSameEmployeeInOtherSessionOfSameCourse() {
    Course course = saveCourse();
    CourseSession sessionA = saveSession(course, 10);
    CourseSession sessionB = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(enrollment(sessionA, EMPLOYEE, EnrollmentStatus.CONFIRMED));

    Enrollment other =
        enrollmentRepository.saveAndFlush(
            enrollment(sessionB, EMPLOYEE, EnrollmentStatus.CONFIRMED));

    assertThat(other.getId()).isNotNull();
  }

  @Test
  void countsBySessionAndStatus() {
    Course course = saveCourse();
    CourseSession sessionA = saveSession(course, 10);
    CourseSession sessionB = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(enrollment(sessionA, "E1", EnrollmentStatus.CONFIRMED));
    enrollmentRepository.saveAndFlush(enrollment(sessionA, "E2", EnrollmentStatus.CONFIRMED));
    enrollmentRepository.saveAndFlush(enrollment(sessionA, "E3", EnrollmentStatus.WAITING));
    enrollmentRepository.saveAndFlush(enrollment(sessionB, "E4", EnrollmentStatus.CONFIRMED));

    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                sessionA.getId(), EnrollmentStatus.CONFIRMED))
        .isEqualTo(2);
    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                sessionA.getId(), EnrollmentStatus.WAITING))
        .isEqualTo(1);
    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                sessionB.getId(), EnrollmentStatus.WAITING))
        .isZero();
  }

  @Test
  void countsConfirmedBySessionIdsExcludingWaitingAndOtherSessions() {
    Course course = saveCourse();
    CourseSession sessionA = saveSession(course, 10);
    CourseSession sessionB = saveSession(course, 10);
    CourseSession sessionC = saveSession(course, 10);
    CourseSession sessionD = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(enrollment(sessionA, "E1", EnrollmentStatus.CONFIRMED));
    enrollmentRepository.saveAndFlush(enrollment(sessionA, "E2", EnrollmentStatus.CONFIRMED));
    enrollmentRepository.saveAndFlush(enrollment(sessionA, "E3", EnrollmentStatus.WAITING));
    enrollmentRepository.saveAndFlush(enrollment(sessionB, "E4", EnrollmentStatus.WAITING));
    enrollmentRepository.saveAndFlush(enrollment(sessionD, "E5", EnrollmentStatus.CONFIRMED));

    var counts =
        enrollmentRepository.countBySessionIdsAndStatus(
            List.of(sessionA.getId(), sessionB.getId(), sessionC.getId()),
            EnrollmentStatus.CONFIRMED);

    assertThat(counts).hasSize(1);
    assertThat(counts.get(0).getSessionId()).isEqualTo(sessionA.getId());
    assertThat(counts.get(0).getConfirmed()).isEqualTo(2);
  }

  @Test
  void existsByActiveStatusOnlyForSameSessionAndEmployee() {
    Course course = saveCourse();
    CourseSession sessionA = saveSession(course, 10);
    CourseSession sessionB = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(enrollment(sessionA, EMPLOYEE, EnrollmentStatus.WAITING));
    var active = EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING);

    assertThat(
            enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(
                sessionA.getId(), EMPLOYEE, active))
        .isTrue();
    assertThat(
            enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(
                sessionA.getId(), "E9999", active))
        .isFalse();
    assertThat(
            enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(
                sessionB.getId(), EMPLOYEE, active))
        .isFalse();
  }

  private Course saveCourse() {
    return entityManager.persistAndFlush(new Course("JPA 입문", "개발", "JPA 기초 과정"));
  }

  private CourseSession saveSession(Course course, int capacity) {
    return entityManager.persistAndFlush(new CourseSession(course, START_AT, "본사 3층", capacity));
  }

  private Enrollment enrollment(CourseSession session, String employeeId, EnrollmentStatus status) {
    return new Enrollment(session, employeeId, status, APPLIED_AT);
  }
}
