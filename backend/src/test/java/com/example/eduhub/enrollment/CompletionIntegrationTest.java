package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.CourseSessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 MariaDB에서 수료 처리를 확인한다(FR-08 C1~C3). 공유 DB이므로 자기 회차만 지우고 사번은 {@code CP-} 접두어를 쓴다.
 *
 * <p>회차는 미래 시작으로 만들어 신청 데이터를 넣은 뒤, 테스트 전용으로 시작 시각을 과거로 당기고 {@code close(now)}로 종료한다. 서비스는 실제 시계를
 * 쓰므로 시작 시각이 과거여야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CompletionIntegrationTest {

  private static final String CONFIRMED_1 = "CP-C1";
  private static final String CONFIRMED_2 = "CP-C2";
  private static final String WAITING = "CP-W1";
  private static final String CANCELLED = "CP-X1";
  private static final String NOT_ENROLLED = "CP-N1";
  private static final String ALREADY_DONE = "CP-D1";
  private static final String RE_ENROLLED = "CP-R1";

  @Autowired private CompletionService completionService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private Clock clock;

  @PersistenceContext private EntityManager entityManager;

  private Course course;
  private CourseSession session;
  private Instant alreadyDoneAt;

  @BeforeEach
  void setUp() {
    Instant now = clock.instant();
    course = courseRepository.save(new Course("CP-과정", "개발", "수료 통합 테스트"));
    session = sessionRepository.save(new CourseSession(course, now.plusSeconds(3600), "CP-장소", 10));
    Instant applied = now.minusSeconds(7200);

    enrollmentRepository.save(Enrollment.confirmed(session, CONFIRMED_1, applied));
    enrollmentRepository.save(Enrollment.confirmed(session, CONFIRMED_2, applied.plusSeconds(1)));
    enrollmentRepository.save(Enrollment.waiting(session, WAITING, applied.plusSeconds(2), 1L));
    Enrollment cancelled =
        enrollmentRepository.save(Enrollment.confirmed(session, CANCELLED, applied));
    cancelled.cancel(applied.plusSeconds(10));
    enrollmentRepository.save(cancelled);
    Enrollment done =
        enrollmentRepository.save(Enrollment.confirmed(session, ALREADY_DONE, applied));
    alreadyDoneAt = now.minusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    done.complete(alreadyDoneAt);
    enrollmentRepository.save(done);
    // 재신청: 취소 행 뒤에 확정 행이 있다.
    Enrollment first =
        enrollmentRepository.save(Enrollment.confirmed(session, RE_ENROLLED, applied));
    first.cancel(applied.plusSeconds(20));
    enrollmentRepository.save(first);
    enrollmentRepository.save(Enrollment.confirmed(session, RE_ENROLLED, applied.plusSeconds(30)));

    // 테스트 전용: 시작 시각을 과거로 당긴 뒤 회차를 종료한다.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> {
          entityManager
              .createQuery("update CourseSession s set s.startAt = :startAt where s.id = :id")
              .setParameter("startAt", now.minusSeconds(3600))
              .setParameter("id", session.getId())
              .executeUpdate();
          entityManager.clear();
          CourseSession loaded = sessionRepository.findById(session.getId()).orElseThrow();
          loaded.close(now);
          sessionRepository.save(loaded);
        });
  }

  @AfterEach
  void tearDown() {
    // setUp이 중간에 실패해도 NPE 없이, 만들어진 것까지만 지운다.
    Course createdCourse = course;
    CourseSession createdSession = session;
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> {
          if (createdSession != null) {
            entityManager
                .createQuery("delete from Enrollment e where e.session.id = :id")
                .setParameter("id", createdSession.getId())
                .executeUpdate();
            entityManager
                .createQuery("delete from CourseSession s where s.id = :id")
                .setParameter("id", createdSession.getId())
                .executeUpdate();
          }
          if (createdCourse != null) {
            entityManager
                .createQuery("delete from Course c where c.id = :id")
                .setParameter("id", createdCourse.getId())
                .executeUpdate();
          }
        });
  }

  private List<Enrollment> rows(String employeeId) {
    return enrollmentRepository.findBySessionIdAndEmployeeIdIn(
        session.getId(), List.of(employeeId));
  }

  @Test
  void completesConfirmedAndLeavesEveryoneElseUntouched() {
    CompletionResponse response =
        completionService.complete(
            session.getId(),
            List.of(
                CONFIRMED_1,
                WAITING,
                CONFIRMED_2,
                CANCELLED,
                NOT_ENROLLED,
                ALREADY_DONE,
                RE_ENROLLED,
                CONFIRMED_1));

    assertThat(response.completed()).containsExactly(CONFIRMED_1, CONFIRMED_2, RE_ENROLLED);
    assertThat(response.failed())
        .extracting(CompletionResponse.Failure::employeeId, CompletionResponse.Failure::reason)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(WAITING, CompletionFailureReason.NOT_CONFIRMED),
            org.assertj.core.groups.Tuple.tuple(CANCELLED, CompletionFailureReason.CANCELLED),
            org.assertj.core.groups.Tuple.tuple(NOT_ENROLLED, CompletionFailureReason.NOT_ENROLLED),
            org.assertj.core.groups.Tuple.tuple(
                ALREADY_DONE, CompletionFailureReason.ALREADY_COMPLETED));

    for (String id : List.of(CONFIRMED_1, CONFIRMED_2)) {
      Enrollment row = rows(id).get(0);
      assertThat(row.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
      assertThat(row.getCompletedAt()).isNotNull();
      assertThat(row.getConfirmedAt()).isNotNull();
    }
    // 재신청 사원은 활성(확정) 행만 수료되고 옛 취소 행은 그대로다.
    List<Enrollment> reRows = rows(RE_ENROLLED);
    assertThat(reRows)
        .extracting(Enrollment::getStatus)
        .containsExactlyInAnyOrder(EnrollmentStatus.COMPLETED, EnrollmentStatus.CANCELLED);
    // 대기자, 취소 행, 이미 수료한 행은 그대로다.
    Enrollment waiting = rows(WAITING).get(0);
    assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(waiting.getWaitingKey()).isEqualTo(1L);
    assertThat(waiting.getCompletedAt()).isNull();
    assertThat(rows(CANCELLED))
        .extracting(Enrollment::getStatus)
        .containsExactly(EnrollmentStatus.CANCELLED);
    assertThat(rows(NOT_ENROLLED)).isEmpty();
    Enrollment done = rows(ALREADY_DONE).get(0);
    assertThat(done.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(done.getCompletedAt()).isEqualTo(alreadyDoneAt);
    assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus())
        .isEqualTo(CourseSessionStatus.CLOSED);
  }

  @Test
  void secondCallReportsAlreadyCompleted() {
    completionService.complete(session.getId(), List.of(CONFIRMED_1));

    CompletionResponse again = completionService.complete(session.getId(), List.of(CONFIRMED_1));

    assertThat(again.completed()).isEmpty();
    assertThat(again.failed())
        .extracting(CompletionResponse.Failure::reason)
        .containsExactly(CompletionFailureReason.ALREADY_COMPLETED);
  }
}
