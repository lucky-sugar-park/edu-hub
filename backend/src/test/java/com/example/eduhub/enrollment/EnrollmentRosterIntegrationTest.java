package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 실제 DB 행에서 {@code getRoster}를 거쳐 응답까지 잇는 테스트. 롤백되는 슬라이스 테스트라 데이터가 남지 않는다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({EnrollmentQueryService.class, WaitingNumbers.class})
class EnrollmentRosterIntegrationTest {

  private static final Instant START_AT = Instant.parse("2026-12-01T01:00:00Z");
  private static final Instant T0 = Instant.parse("2026-11-12T01:00:00Z");

  @Autowired private EnrollmentQueryService service;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void rosterFromDatabaseRowsIsSortedNumberedAndFiltered() {
    Course course = entityManager.persistAndFlush(new Course("JPA 입문", "개발", "기초"));
    CourseSession session =
        entityManager.persistAndFlush(new CourseSession(course, START_AT, "본사 3층", 7));
    CourseSession other =
        entityManager.persistAndFlush(new CourseSession(course, START_AT, "본사 4층", 7));

    // 확정 시각이 다른 확정 2명과 수료 1명. 저장 순서와 확정 시각 순서를 일부러 다르게 한다.
    save(Enrollment.confirmed(session, "C-LATE", T0.plusSeconds(30)));
    save(Enrollment.confirmed(session, "C-EARLY", T0.plusSeconds(10)));
    Enrollment done = save(Enrollment.confirmed(session, "C-DONE", T0.plusSeconds(20)));
    Instant completedAt = T0.plusSeconds(9000);
    done.complete(completedAt);
    save(done);
    // 대기 3명(키 4, -2, 1)을 키 순서와 다르게 저장한다.
    save(Enrollment.waiting(session, "W-4", T0.plusSeconds(1), 4L));
    save(Enrollment.waiting(session, "W-NEG2", T0.plusSeconds(2), -2L));
    save(Enrollment.waiting(session, "W-1", T0.plusSeconds(3), 1L));
    // 취소 1명과 다른 회차 신청은 빠져야 한다.
    Enrollment gone = save(Enrollment.confirmed(session, "GONE", T0));
    gone.cancel(T0.plusSeconds(5));
    save(gone);
    save(Enrollment.confirmed(other, "OTHER", T0));
    entityManager.clear();

    SessionRosterResponse roster = service.getRoster(session.getId());

    assertThat(roster.sessionId()).isEqualTo(session.getId());
    assertThat(roster.capacity()).isEqualTo(7);
    assertThat(roster.sessionStatus()).isEqualTo(CourseSessionStatus.OPEN);

    assertThat(roster.confirmed())
        .extracting(SessionRosterResponse.ConfirmedEntry::employeeId)
        .containsExactly("C-EARLY", "C-DONE", "C-LATE");
    assertThat(roster.confirmed())
        .extracting(SessionRosterResponse.ConfirmedEntry::status)
        .containsExactly(
            EnrollmentStatus.CONFIRMED, EnrollmentStatus.COMPLETED, EnrollmentStatus.CONFIRMED);
    assertThat(roster.confirmed())
        .extracting(SessionRosterResponse.ConfirmedEntry::completedAt)
        .containsExactly(null, completedAt, null);
    assertThat(roster.confirmed().get(0).confirmedAt()).isEqualTo(T0.plusSeconds(10));

    assertThat(roster.waiting())
        .extracting(SessionRosterResponse.WaitingEntry::employeeId)
        .containsExactly("W-NEG2", "W-1", "W-4");
    assertThat(roster.waiting())
        .extracting(SessionRosterResponse.WaitingEntry::waitingNumber)
        .containsExactly(1, 2, 3);
    // 각 순번은 D-23 식(같은 회차에서 키가 더 작은 대기자 수 + 1)과 같다.
    for (SessionRosterResponse.WaitingEntry entry : roster.waiting()) {
      Enrollment row = enrollmentRepository.findById(entry.enrollmentId()).orElseThrow();
      long ahead =
          enrollmentRepository.countBySessionIdAndStatusAndWaitingKeyLessThan(
              session.getId(), EnrollmentStatus.WAITING, row.getWaitingKey());
      assertThat(entry.waitingNumber()).isEqualTo(ahead + 1);
    }

    assertThat(roster.confirmed())
        .extracting(SessionRosterResponse.ConfirmedEntry::employeeId)
        .doesNotContain("GONE", "OTHER");
    assertThat(roster.waiting())
        .extracting(SessionRosterResponse.WaitingEntry::employeeId)
        .doesNotContain("GONE", "OTHER");
  }

  private Enrollment save(Enrollment enrollment) {
    return enrollmentRepository.saveAndFlush(enrollment);
  }
}
