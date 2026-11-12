package com.example.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.enrollment.CapacityChangeEnrollmentHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 회차 수정·종료가 회차 행 락을 기다리는지 결정적으로 확인한다(D-20, D-24). 다른 트랜잭션이 락을 쥔 동안 호출이 끝나지 않아야 한다. 각 스레드가 자기
 * 트랜잭션으로 커밋해야 하므로 {@code @Transactional}을 붙이지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionLockIntegrationTest {

  private static final long HOLD_MILLIS = 1500;

  @Autowired private CourseCommandService courseCommandService;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository courseSessionRepository;
  @MockitoSpyBean private CapacityChangeEnrollmentHandler capacityChangeHandler;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<Long> sessionIds = new ArrayList<>();
  private final List<Long> courseIds = new ArrayList<>();

  /** 이 테스트가 만든 신청, 회차, 과정만 지운다. */
  @AfterEach
  void cleanUp() {
    for (Long sessionId : sessionIds) {
      jdbcTemplate.update("DELETE FROM enrollment WHERE session_id = ?", sessionId);
      jdbcTemplate.update("DELETE FROM course_session WHERE id = ?", sessionId);
    }
    for (Long courseId : courseIds) {
      jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
    }
    sessionIds.clear();
    courseIds.clear();
  }

  /**
   * 점유자가 회차 행을 잡고 정원을 7로 바꾼 채 커밋을 미룬다. 락을 잡는 updateSession은 커밋 뒤의 값 7을 읽어 (7, 5)로 핸들러를 부른다. 락 없는
   * 읽기는 옛 값 10을 읽는다.
   */
  @Test
  void updateSessionWaitsForSessionRowLock() throws Exception {
    Instant startAt = Instant.now().plus(Duration.ofDays(7));
    Long sessionId = givenSession(startAt);

    Outcome outcome =
        runWhileLocked(
            sessionId,
            () ->
                jdbcTemplate.update(
                    "UPDATE course_session SET capacity = 7 WHERE id = ?", sessionId),
            () ->
                courseCommandService.updateSession(
                    sessionId, new SessionRequest(startAt, "본사", 5)));

    assertThat(outcome.error()).as("updateSession 예외").isNull();
    assertThat(outcome.elapsedMillis()).isGreaterThanOrEqualTo(HOLD_MILLIS - 100);
    verify(capacityChangeHandler).onCapacityChanged(any(), eq(7), eq(5));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT capacity FROM course_session WHERE id = ?", Integer.class, sessionId))
        .isEqualTo(5);
  }

  /**
   * 점유자가 회차를 CLOSED로 바꿔 커밋하면, 락을 잡는 closeSession은 SESSION_CLOSED를 본다. 락 없는 읽기는 옛 상태 OPEN을 읽어 성공해
   * 버린다.
   */
  @Test
  void closeSessionWaitsForSessionRowLock() throws Exception {
    Long sessionId = givenSession(Instant.now().minus(Duration.ofDays(1)));

    Outcome outcome =
        runWhileLocked(
            sessionId,
            () ->
                jdbcTemplate.update(
                    "UPDATE course_session SET status = 'CLOSED' WHERE id = ?", sessionId),
            () -> courseCommandService.closeSession(sessionId));

    assertThat(outcome.error()).isInstanceOf(ConflictException.class);
    assertThat(((ConflictException) outcome.error()).getCode()).isEqualTo("SESSION_CLOSED");
    assertThat(outcome.elapsedMillis()).isGreaterThanOrEqualTo(HOLD_MILLIS - 100);
  }

  @Test
  void capacityChangeHandlerRequiresTransaction() {
    Long sessionId = givenSession(Instant.now().plus(Duration.ofDays(7)));
    CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();

    assertThatThrownBy(() -> capacityChangeHandler.onCapacityChanged(session, 10, 5))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private record Outcome(long elapsedMillis, Throwable error) {}

  /**
   * 다른 트랜잭션이 회차 행을 FOR UPDATE로 잡고 holderWork를 실행한 뒤 HOLD_MILLIS 동안 커밋을 미루는 사이에 action을 호출한다. 걸린 시간과
   * 던져진 예외를 돌려준다.
   */
  private Outcome runWhileLocked(Long sessionId, Runnable holderWork, Callable<Object> action)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch locked = new CountDownLatch(1);
      Future<?> holder =
          executor.submit(() ->
              new TransactionTemplate(transactionManager)
                  .executeWithoutResult(status -> {
                    courseSessionRepository.findByIdForUpdate(sessionId).orElseThrow();
                    holderWork.run();
                    locked.countDown();
                    sleep(HOLD_MILLIS);
                  }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).as("락 획득 시간 초과").isTrue();

      long begin = System.nanoTime();
      Future<Object> worker = executor.submit(action);
      Throwable error = null;
      try {
        worker.get(20, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        error = e.getCause();
      }
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
      holder.get(10, TimeUnit.SECONDS);
      return new Outcome(elapsed, error);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Long givenSession(Instant startAt) {
    Course course = courseRepository.save(new Course("락 테스트 과정", "테스트", null));
    courseIds.add(course.getId());
    CourseSession session =
        courseSessionRepository.save(new CourseSession(course, startAt, "본사", 10));
    sessionIds.add(session.getId());
    return session.getId();
  }
}
