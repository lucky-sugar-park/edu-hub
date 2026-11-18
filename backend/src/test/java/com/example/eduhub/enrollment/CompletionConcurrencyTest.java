package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수료 동시성 테스트(FR-08, CLAUDE.md 6). 실제 MariaDB(eduhub_test)에서 돌리며, 각 스레드가 자기 트랜잭션으로 커밋해야 하므로
 * {@code @Transactional}을 붙이지 않는다. 회차 락({@code findByIdForUpdate})을 빼면 같은 사번이 여러 번 수료 처리되어 실패해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CompletionConcurrencyTest {

  private static final int THREADS = 32;
  private static final long TIMEOUT_SECONDS = 30;

  @Autowired private CompletionService completionService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;

  @PersistenceContext private EntityManager entityManager;

  private final List<Long> sessionIds = new ArrayList<>();
  private final List<Long> courseIds = new ArrayList<>();

  /** 이 테스트가 만든 신청, 회차, 과정만 지운다. */
  @AfterEach
  void cleanUp() {
    for (Long sessionId : sessionIds) {
      jdbcTemplate.update("DELETE FROM enrollment WHERE session_id = ?", sessionId);
    }
    for (Long sessionId : sessionIds) {
      jdbcTemplate.update("DELETE FROM course_session WHERE id = ?", sessionId);
    }
    for (Long courseId : courseIds) {
      jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
    }
    sessionIds.clear();
    courseIds.clear();
  }

  @Test
  @DisplayName("CC1: 확정자 1명을 32개 스레드가 동시에 수료 요청하면 1건만 성공하고 나머지는 이미 수료다")
  void oneConfirmedWithManyThreads() throws Exception {
    Long sessionId = givenClosedSession(List.of("CC-ONE"), 0);

    List<CompletionResponse> responses = runConcurrently(sessionId, List.of("CC-ONE"));

    long completed = responses.stream().mapToLong(r -> r.completed().size()).sum();
    long already =
        responses.stream()
            .flatMap(r -> r.failed().stream())
            .filter(f -> f.reason() == CompletionFailureReason.ALREADY_COMPLETED)
            .count();
    String state = "성공 %d, 이미 수료 %d".formatted(completed, already);
    assertThat(completed).as(state).isEqualTo(1);
    assertThat(already).as(state).isEqualTo(THREADS - 1);

    List<Enrollment> rows = rows(sessionId);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(rows.get(0).getCompletedAt()).as(state).isNotNull();
  }

  @Test
  @DisplayName("CC2: 확정자 10명을 32개 스레드가 같은 목록으로 요청하면 사번마다 1번만 성공하고 대기자는 그대로다")
  void tenConfirmedWithManyThreads() throws Exception {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      ids.add("CC-" + i);
    }
    Long sessionId = givenClosedSession(ids, 2);

    List<CompletionResponse> responses = runConcurrently(sessionId, ids);

    for (String id : ids) {
      long completed = responses.stream().filter(r -> r.completed().contains(id)).count();
      long already =
          responses.stream()
              .flatMap(r -> r.failed().stream())
              .filter(f ->
                  f.employeeId().equals(id)
                      && f.reason() == CompletionFailureReason.ALREADY_COMPLETED)
              .count();
      assertThat(completed).as("%s 성공 %d, 이미 수료 %d".formatted(id, completed, already)).isEqualTo(1);
      assertThat(already).as(id).isEqualTo(THREADS - 1);
    }
    List<Enrollment> rows = rows(sessionId);
    assertThat(rows.stream().filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED)).hasSize(10);
    assertThat(rows.stream().filter(e -> e.getStatus() == EnrollmentStatus.WAITING)).hasSize(2);
    assertThat(rows.stream().filter(e -> e.getStatus() == EnrollmentStatus.WAITING))
        .allSatisfy(e -> assertThat(e.getCompletedAt()).isNull());
  }

  private List<Enrollment> rows(Long sessionId) {
    return enrollmentRepository.findBySessionIdAndStatusIn(
        sessionId,
        List.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.COMPLETED, EnrollmentStatus.WAITING));
  }

  /** 확정자와 대기자를 넣고, 시작 시각을 과거로 당긴 뒤 종료한 회차를 만든다(테스트 전용). */
  private Long givenClosedSession(List<String> confirmedIds, int waitingCount) {
    Instant now = clock.instant();
    Course course = courseRepository.save(new Course("CC-과정", "개발", "수료 동시성 테스트"));
    courseIds.add(course.getId());
    CourseSession session =
        sessionRepository.save(new CourseSession(course, now.plusSeconds(3600), "CC-장소", 20));
    sessionIds.add(session.getId());
    Instant applied = now.minusSeconds(7200);
    for (String id : confirmedIds) {
      enrollmentRepository.save(Enrollment.confirmed(session, id, applied));
    }
    for (int i = 0; i < waitingCount; i++) {
      enrollmentRepository.save(Enrollment.waiting(session, "CC-W" + i, applied, i + 1L));
    }
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
    return session.getId();
  }

  /** 32개 스레드를 래치로 동시에 출발시킨다. 예외(LOCK_TIMEOUT 포함)가 나면 테스트가 실패한다. */
  private List<CompletionResponse> runConcurrently(Long sessionId, List<String> ids)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      CountDownLatch ready = new CountDownLatch(THREADS);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<CompletionResponse>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            executor.submit(() -> {
              ready.countDown();
              start.await();
              return completionService.complete(sessionId, ids);
            }));
      }
      assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("스레드 준비 시간 초과").isTrue();
      start.countDown();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      List<CompletionResponse> responses = new ArrayList<>();
      for (Future<CompletionResponse> future : futures) {
        long remaining = Math.max(0, deadline - System.nanoTime());
        try {
          responses.add(future.get(remaining, TimeUnit.NANOSECONDS));
        } catch (TimeoutException exception) {
          throw new AssertionError("전체 마감 %d초 초과".formatted(TIMEOUT_SECONDS), exception);
        }
      }
      return responses;
    } finally {
      executor.shutdownNow();
      // 워커가 끝날 때까지 기다려 뒤이은 정리 DELETE와 겹치지 않게 한다.
      executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }
}
