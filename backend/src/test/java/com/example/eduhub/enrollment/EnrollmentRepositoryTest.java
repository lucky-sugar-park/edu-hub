package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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

  @Test
  void rejectsSameWaitingKeyInSameSession() {
    CourseSession session = saveSession(saveCourse(), 10);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E1", APPLIED_AT, 1L));

    assertThatThrownBy(() ->
            enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E2", APPLIED_AT, 1L)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_enrollment_waiting_key");
  }

  @Test
  void allowsSameWaitingKeyInOtherSession() {
    Course course = saveCourse();
    CourseSession sessionA = saveSession(course, 10);
    CourseSession sessionB = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(sessionA, "E1", APPLIED_AT, 1L));

    Enrollment other =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(sessionB, "E1", APPLIED_AT, 1L));

    assertThat(other.getId()).isNotNull();
  }

  @Test
  void allowsManyNullWaitingKeysInSameSession() {
    CourseSession session = saveSession(saveCourse(), 10);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E1", APPLIED_AT));
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E2", APPLIED_AT));
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E3", APPLIED_AT));

    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                session.getId(), EnrollmentStatus.CONFIRMED))
        .isEqualTo(3);
  }

  @Test
  void findsMaxAndMinWaitingKeyOnlyAmongWaiting() {
    Course course = saveCourse();
    CourseSession session = saveSession(course, 10);
    CourseSession other = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E1", APPLIED_AT));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E2", APPLIED_AT, -2L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E3", APPLIED_AT, 5L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(other, "E4", APPLIED_AT, 99L));
    // 대기가 아니게 된 행의 키는 무시한다.
    Enrollment cancelled =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E5", APPLIED_AT, 50L));
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE enrollment SET status = 'CANCELLED' WHERE id = ?1")
        .setParameter(1, cancelled.getId())
        .executeUpdate();
    entityManager.clear();

    assertThat(enrollmentRepository.findMaxWaitingKey(session.getId())).contains(5L);
    assertThat(enrollmentRepository.findMinWaitingKey(session.getId())).contains(-2L);
  }

  @Test
  void returnsEmptyWaitingKeyWhenNobodyIsWaiting() {
    CourseSession session = saveSession(saveCourse(), 10);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E1", APPLIED_AT));

    assertThat(enrollmentRepository.findMaxWaitingKey(session.getId())).isEmpty();
    assertThat(enrollmentRepository.findMinWaitingKey(session.getId())).isEmpty();
  }

  @Test
  void countsOnlyLowerWaitingKeysInSameSession() {
    Course course = saveCourse();
    CourseSession session = saveSession(course, 10);
    CourseSession other = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E1", APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E2", APPLIED_AT, 2L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E3", APPLIED_AT, 3L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(other, "E4", APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E5", APPLIED_AT));

    assertThat(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                session.getId(), EnrollmentStatus.WAITING, 3L))
        .isEqualTo(2);
    assertThat(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                session.getId(), EnrollmentStatus.WAITING, 1L))
        .isZero();
  }

  @Test
  void findsLatestConfirmedFirstWithIdDescTieBreak() {
    CourseSession session = saveSession(saveCourse(), 10);
    Instant t1 = APPLIED_AT;
    Instant t2 = APPLIED_AT.plusSeconds(10);
    Enrollment first = enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E1", t1));
    Enrollment tieA = enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E2", t2));
    Enrollment tieB = enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E3", t2));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E4", t2, 1L));

    var all =
        enrollmentRepository.findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(
            session.getId(), EnrollmentStatus.CONFIRMED, PageRequest.of(0, 10));
    var top2 =
        enrollmentRepository.findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(
            session.getId(), EnrollmentStatus.CONFIRMED, PageRequest.of(0, 2));

    assertThat(all)
        .extracting(Enrollment::getId)
        .containsExactly(tieB.getId(), tieA.getId(), first.getId());
    assertThat(top2).extracting(Enrollment::getId).containsExactly(tieB.getId(), tieA.getId());
  }

  @Test
  void findsOnlyActiveEnrollmentOfEmployee() {
    Course course = saveCourse();
    CourseSession session = saveSession(course, 10);
    CourseSession other = saveSession(course, 10);
    Enrollment cancelled =
        enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, EMPLOYEE, APPLIED_AT));
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE enrollment SET status = 'CANCELLED' WHERE id = ?1")
        .setParameter(1, cancelled.getId())
        .executeUpdate();
    entityManager.clear();
    var active = EnumSet.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING);

    assertThat(
            enrollmentRepository.findBySessionIdAndEmployeeIdAndStatusIn(
                session.getId(), EMPLOYEE, active))
        .isEmpty();

    Enrollment live =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, EMPLOYEE, APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(other, "E9", APPLIED_AT));

    assertThat(
            enrollmentRepository.findBySessionIdAndEmployeeIdAndStatusIn(
                session.getId(), EMPLOYEE, active))
        .get()
        .extracting(Enrollment::getId)
        .isEqualTo(live.getId());
    assertThat(
            enrollmentRepository.findBySessionIdAndEmployeeIdAndStatusIn(
                other.getId(), EMPLOYEE, active))
        .isEmpty();
  }

  private final AtomicLong nextWaitingKey = new AtomicLong(1);

  private Enrollment enrollment(CourseSession session, String employeeId, EnrollmentStatus status) {
    return status == EnrollmentStatus.WAITING
        ? Enrollment.waiting(session, employeeId, APPLIED_AT, nextWaitingKey.getAndIncrement())
        : Enrollment.confirmed(session, employeeId, APPLIED_AT);
  }
}
