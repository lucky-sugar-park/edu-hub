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

  @Test
  void findsWaitingByKeyAscendingIncludingNegativeKeysWithPageSize() {
    Course course = saveCourse();
    CourseSession session = saveSession(course, 10);
    CourseSession other = saveSession(course, 10);
    Enrollment k5 =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E1", APPLIED_AT, 5L));
    Enrollment kNeg2 =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E2", APPLIED_AT, -2L));
    Enrollment k1 =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E3", APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, "E4", APPLIED_AT));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(other, "E5", APPLIED_AT, -9L));

    var all =
        enrollmentRepository.findBySessionIdAndStatusOrderByWaitingKeyAsc(
            session.getId(), EnrollmentStatus.WAITING, PageRequest.of(0, 10));
    var top2 =
        enrollmentRepository.findBySessionIdAndStatusOrderByWaitingKeyAsc(
            session.getId(), EnrollmentStatus.WAITING, PageRequest.of(0, 2));

    assertThat(all)
        .extracting(Enrollment::getId)
        .containsExactly(kNeg2.getId(), k1.getId(), k5.getId());
    assertThat(top2).extracting(Enrollment::getId).containsExactly(kNeg2.getId(), k1.getId());
  }

  @Test
  void allowsReEnrollmentAfterRealCancel() {
    CourseSession session = saveSession(saveCourse(), 10);
    Enrollment first =
        enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, EMPLOYEE, APPLIED_AT));
    first.cancel(APPLIED_AT.plusSeconds(60));
    enrollmentRepository.saveAndFlush(first);

    Enrollment second =
        enrollmentRepository.saveAndFlush(Enrollment.confirmed(session, EMPLOYEE, APPLIED_AT));

    assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
    assertThat(first.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
  }

  @Test
  void cancelledWaitingRowDoesNotConflictOnWaitingKeyIndex() {
    CourseSession session = saveSession(saveCourse(), 10);
    Enrollment waiting =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, EMPLOYEE, APPLIED_AT, 1L));
    waiting.cancel(APPLIED_AT.plusSeconds(60));
    enrollmentRepository.saveAndFlush(waiting);

    Enrollment sameKey =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E2", APPLIED_AT, 1L));
    Enrollment secondCancelled =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "E3", APPLIED_AT, 2L));
    secondCancelled.cancel(APPLIED_AT.plusSeconds(61));
    enrollmentRepository.saveAndFlush(secondCancelled);

    assertThat(sameKey.getId()).isNotNull();
    assertThat(waiting.getWaitingKey()).isNull();
    assertThat(secondCancelled.getWaitingKey()).isNull();
  }

  private static final String ME = "MY-A";
  private static final String OTHER = "MY-B";

  @Test
  void myEnrollmentsReturnsOnlyOwnRowsNewestFirst() {
    Course course = saveCourse();
    CourseSession s1 = saveSession(course, 10);
    CourseSession s2 = saveSession(course, 10);
    CourseSession s3 = saveSession(course, 10);
    Enrollment confirmed =
        enrollmentRepository.saveAndFlush(Enrollment.confirmed(s1, ME, APPLIED_AT));
    Enrollment waiting =
        enrollmentRepository.saveAndFlush(
            Enrollment.waiting(s2, ME, APPLIED_AT.plusSeconds(10), 1L));
    Enrollment cancelled =
        enrollmentRepository.saveAndFlush(Enrollment.confirmed(s3, ME, APPLIED_AT.plusSeconds(20)));
    cancelled.cancel(APPLIED_AT.plusSeconds(30));
    enrollmentRepository.saveAndFlush(cancelled);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(s1, OTHER, APPLIED_AT));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, OTHER, APPLIED_AT, 2L));
    entityManager.clear();

    var page = enrollmentRepository.findMyEnrollments(ME, null, PageRequest.of(0, 20));

    assertThat(page.getContent())
        .extracting(MyEnrollmentRow::id)
        .containsExactly(cancelled.getId(), waiting.getId(), confirmed.getId());
    assertThat(page.getTotalElements()).isEqualTo(3);
    MyEnrollmentRow first = page.getContent().get(0);
    assertThat(first.courseTitle()).isEqualTo("JPA 입문");
    assertThat(first.sessionId()).isEqualTo(s3.getId());
    assertThat(first.sessionStartAt()).isEqualTo(START_AT);
    assertThat(first.sessionPlace()).isEqualTo("본사 3층");
    assertThat(first.appliedAt()).isEqualTo(APPLIED_AT.plusSeconds(20));
  }

  @Test
  void myEnrollmentsFiltersByStatus() {
    Course course = saveCourse();
    CourseSession s1 = saveSession(course, 10);
    CourseSession s2 = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(s1, ME, APPLIED_AT));
    Enrollment waiting =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, ME, APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, OTHER, APPLIED_AT, 2L));
    CourseSession s3 = saveSession(course, 10);
    Enrollment cancelled =
        enrollmentRepository.saveAndFlush(Enrollment.confirmed(s3, ME, APPLIED_AT));
    cancelled.cancel(APPLIED_AT.plusSeconds(5));
    enrollmentRepository.saveAndFlush(cancelled);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(s3, OTHER, APPLIED_AT));
    entityManager.clear();

    var page =
        enrollmentRepository.findMyEnrollments(ME, EnrollmentStatus.WAITING, PageRequest.of(0, 20));
    var cancelledPage =
        enrollmentRepository.findMyEnrollments(
            ME, EnrollmentStatus.CANCELLED, PageRequest.of(0, 20));

    assertThat(page.getContent()).extracting(MyEnrollmentRow::id).containsExactly(waiting.getId());
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(cancelledPage.getContent())
        .extracting(MyEnrollmentRow::id)
        .containsExactly(cancelled.getId());
    assertThat(cancelledPage.getContent().get(0).waitingNumber()).isNull();
    assertThat(cancelledPage.getTotalElements()).isEqualTo(1);
  }

  @Test
  void myEnrollmentsWaitingNumberMatchesWaitingNumbersFormula() {
    Course course = saveCourse();
    CourseSession s1 = saveSession(course, 10);
    CourseSession s2 = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s1, OTHER, APPLIED_AT, 1L));
    Enrollment w1 =
        enrollmentRepository.saveAndFlush(
            Enrollment.waiting(s1, ME, APPLIED_AT.plusSeconds(1), 2L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, OTHER, APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, "MY-C", APPLIED_AT, 2L));
    Enrollment w2 =
        enrollmentRepository.saveAndFlush(
            Enrollment.waiting(s2, ME, APPLIED_AT.plusSeconds(2), 3L));
    // 같은 회차의 확정·취소 행은 순번에 섞이지 않는다.
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(s2, "MY-D", APPLIED_AT));
    Enrollment gone =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, "MY-E", APPLIED_AT, 0L));
    gone.cancel(APPLIED_AT.plusSeconds(5));
    enrollmentRepository.saveAndFlush(gone);
    entityManager.clear();

    var rows = enrollmentRepository.findMyEnrollments(ME, null, PageRequest.of(0, 20));

    MyEnrollmentRow r1 = rowOf(rows.getContent(), w1.getId());
    MyEnrollmentRow r2 = rowOf(rows.getContent(), w2.getId());
    assertThat(r1.waitingNumber()).isEqualTo(2L);
    assertThat(r2.waitingNumber()).isEqualTo(3L);
    assertThat(r1.waitingNumber())
        .isEqualTo(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                    s1.getId(), EnrollmentStatus.WAITING, w1.getWaitingKey())
                + 1);
    assertThat(r2.waitingNumber())
        .isEqualTo(
            enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
                    s2.getId(), EnrollmentStatus.WAITING, w2.getWaitingKey())
                + 1);
  }

  @Test
  void myEnrollmentsWaitingNumberHandlesNegativeKeys() {
    CourseSession session = saveSession(saveCourse(), 10);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, ME, APPLIED_AT, -1L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "MY-C", APPLIED_AT, 1L));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, "MY-D", APPLIED_AT, 2L));
    entityManager.clear();

    assertThat(waitingNumberOf("MY-C", session)).isEqualTo(2L);
    assertThat(waitingNumberOf("MY-D", session)).isEqualTo(3L);
    assertThat(waitingNumberOf(ME, session)).isEqualTo(1L);
  }

  @Test
  void myEnrollmentsWaitingNumberIsNullUnlessWaiting() {
    Course course = saveCourse();
    CourseSession s1 = saveSession(course, 10);
    CourseSession s2 = saveSession(course, 10);
    enrollmentRepository.saveAndFlush(Enrollment.confirmed(s1, ME, APPLIED_AT));
    Enrollment cancelled =
        enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, ME, APPLIED_AT, 1L));
    cancelled.cancel(APPLIED_AT.plusSeconds(5));
    enrollmentRepository.saveAndFlush(cancelled);
    entityManager.clear();

    var rows = enrollmentRepository.findMyEnrollments(ME, null, PageRequest.of(0, 20));

    assertThat(rows.getContent()).hasSize(2);
    assertThat(rows.getContent()).allSatisfy(r -> assertThat(r.waitingNumber()).isNull());
  }

  @Test
  void myEnrollmentsOrdersByIdDescWhenAppliedAtIsSame() {
    Course course = saveCourse();
    Enrollment a =
        enrollmentRepository.saveAndFlush(
            Enrollment.confirmed(saveSession(course, 10), ME, APPLIED_AT));
    Enrollment b =
        enrollmentRepository.saveAndFlush(
            Enrollment.confirmed(saveSession(course, 10), ME, APPLIED_AT));
    entityManager.clear();

    var rows = enrollmentRepository.findMyEnrollments(ME, null, PageRequest.of(0, 20));

    assertThat(rows.getContent())
        .extracting(MyEnrollmentRow::id)
        .containsExactly(b.getId(), a.getId());
  }

  @Test
  void myEnrollmentsPagesWithTotalCount() {
    Course course = saveCourse();
    List<Long> ids = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      ids.add(
          enrollmentRepository
              .saveAndFlush(
                  Enrollment.confirmed(saveSession(course, 10), ME, APPLIED_AT.plusSeconds(i)))
              .getId());
    }
    entityManager.clear();

    var first = enrollmentRepository.findMyEnrollments(ME, null, PageRequest.of(0, 2));
    var last = enrollmentRepository.findMyEnrollments(ME, null, PageRequest.of(2, 2));

    assertThat(first.getContent())
        .extracting(MyEnrollmentRow::id)
        .containsExactly(ids.get(4), ids.get(3));
    assertThat(first.getTotalElements()).isEqualTo(5);
    assertThat(last.getContent()).extracting(MyEnrollmentRow::id).containsExactly(ids.get(0));
    assertThat(last.getTotalElements()).isEqualTo(5);
  }

  private Long waitingNumberOf(String employeeId, CourseSession session) {
    return enrollmentRepository
        .findMyEnrollments(employeeId, null, PageRequest.of(0, 20))
        .getContent()
        .stream()
        .filter(r -> r.sessionId().equals(session.getId()))
        .findFirst()
        .orElseThrow()
        .waitingNumber();
  }

  private static MyEnrollmentRow rowOf(List<MyEnrollmentRow> rows, Long id) {
    return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
  }

  private final AtomicLong nextWaitingKey = new AtomicLong(1);

  private Enrollment enrollment(CourseSession session, String employeeId, EnrollmentStatus status) {
    return status == EnrollmentStatus.WAITING
        ? Enrollment.waiting(session, employeeId, APPLIED_AT, nextWaitingKey.getAndIncrement())
        : Enrollment.confirmed(session, employeeId, APPLIED_AT);
  }
}
