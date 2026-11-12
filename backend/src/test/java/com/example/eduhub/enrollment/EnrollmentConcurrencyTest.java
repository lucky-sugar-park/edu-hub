package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
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

/**
 * 수강 신청 동시성 테스트(NFR-01). 실제 MariaDB(eduhub_test)에서 돌리며, 각 스레드가 자기 트랜잭션으로 커밋해야 하므로
 * {@code @Transactional}을 붙이지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EnrollmentConcurrencyTest {

  private static final int THREADS = 32;
  private static final long TIMEOUT_SECONDS = 30;
  private static final String DUPLICATE = "DUPLICATE_ENROLLMENT";

  @Autowired private EnrollmentCommandService enrollmentCommandService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository courseSessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

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
  @DisplayName("C1: 정원 1에 서로 다른 100명이 동시에 신청하면 확정은 1명뿐이다")
  void capacityOneWithHundredApplicants() throws Exception {
    Long sessionId = givenSession(givenCourse(), 1);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      tasks.add(enrollTask(sessionId, "C1-" + i));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    long confirmedInDb =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    assertThat(distribution)
        .as("결과 분포 %s, DB 확정 수 %d", distribution, confirmedInDb)
        .containsOnly(Map.entry("CONFIRMED", 1), Map.entry("WAITING", 99));
    assertThat(confirmedInDb).as("결과 분포 %s", distribution).isEqualTo(1);
  }

  @Test
  @DisplayName("C2: 정원 10에 9명이 신청한 뒤 100명이 동시에 신청해도 확정은 10명이다")
  void lastSeatWithHundredApplicants() throws Exception {
    Long sessionId = givenSession(givenCourse(), 10);
    for (int i = 0; i < 9; i++) {
      enrollmentCommandService.enroll(sessionId, "C2-PRE-" + i);
    }

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      tasks.add(enrollTask(sessionId, "C2-" + i));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    long confirmedInDb =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    assertThat(distribution)
        .as("결과 분포 %s, DB 확정 수 %d", distribution, confirmedInDb)
        .containsOnly(Map.entry("CONFIRMED", 1), Map.entry("WAITING", 99));
    assertThat(confirmedInDb).as("결과 분포 %s", distribution).isEqualTo(10);
  }

  @Test
  @DisplayName("C3: 같은 사번이 10건을 동시에 신청하면 1건만 성공하고 나머지는 중복이다")
  void sameEmployeeTenTimes() throws Exception {
    Long sessionId = givenSession(givenCourse(), 5);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(enrollTask(sessionId, "C3-SAME"));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    long activeInDb =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED)
            + enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.WAITING);
    assertThat(distribution)
        .as("결과 분포 %s, DB 활성 신청 수 %d", distribution, activeInDb)
        .containsOnly(Map.entry("CONFIRMED", 1), Map.entry(DUPLICATE, 9));
    assertThat(activeInDb).as("결과 분포 %s", distribution).isEqualTo(1);
  }

  @Test
  @DisplayName("C4: 같은 과정의 두 회차에 동시에 신청해도 회차마다 정원 5명만 확정된다")
  void twoSessionsOfSameCourse() throws Exception {
    Long courseId = givenCourse();
    Long sessionA = givenSession(courseId, 5);
    Long sessionB = givenSession(courseId, 5);

    // 두 회차의 신청이 번갈아 섞이도록 한 목록에 담아 한꺼번에 출발시킨다.
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      tasks.add(enrollTask(sessionA, "C4-A-" + i));
      tasks.add(enrollTask(sessionB, "C4-B-" + i));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    long confirmedA =
        enrollmentRepository.countBySessionIdAndStatus(sessionA, EnrollmentStatus.CONFIRMED);
    long confirmedB =
        enrollmentRepository.countBySessionIdAndStatus(sessionB, EnrollmentStatus.CONFIRMED);
    assertThat(distribution)
        .as("결과 분포 %s, DB 확정 수 A=%d, B=%d", distribution, confirmedA, confirmedB)
        .containsOnly(Map.entry("CONFIRMED", 10), Map.entry("WAITING", 90));
    assertThat(confirmedA).as("회차 A, 결과 분포 %s", distribution).isEqualTo(5);
    assertThat(confirmedB).as("회차 B, 결과 분포 %s", distribution).isEqualTo(5);
  }

  private Long givenCourse() {
    Course course = courseRepository.save(new Course("동시성 테스트 과정", "테스트", null));
    courseIds.add(course.getId());
    return course.getId();
  }

  private Long givenSession(Long courseId, int capacity) {
    Course course = courseRepository.findById(courseId).orElseThrow();
    Instant startAt = Instant.now().plus(7, ChronoUnit.DAYS);
    CourseSession session =
        courseSessionRepository.save(new CourseSession(course, startAt, "동시성 테스트실", capacity));
    sessionIds.add(session.getId());
    return session.getId();
  }

  /** 성공이면 상태 이름을, 실패면 ConflictException의 code 또는 예외 클래스 이름을 돌려준다. 예외를 삼키지 않고 분포로 센다. */
  private Callable<String> enrollTask(Long sessionId, String employeeId) {
    return () -> {
      try {
        return enrollmentCommandService.enroll(sessionId, employeeId).status().name();
      } catch (ConflictException exception) {
        return exception.getCode();
      } catch (Throwable throwable) {
        return throwable.getClass().getName();
      }
    };
  }

  /**
   * 작업을 스레드 32개로 동시에 출발시키고 결과 분포(키별 건수)를 돌려준다. 작업 수가 스레드 수보다 많으면 처음 출발하는 스레드 수만큼만 준비 래치를 기다리고, 나머지는
   * 풀이 비는 대로 이어서 실행한다. 준비 래치를 {@code min(작업 수, 32)}로 두는 이유는 스레드 풀이 32개라 그 이상은 동시에 준비될 수 없기 때문이다.
   * HikariCP 기본 풀이 10개라 DB 쪽 동시 실행은 그 이하이고, 같은 회차 신청은 회차 행 락에서 줄을 선다.
   *
   * <p>시작 이후 전체 실행에 30초 마감 시각 하나를 두고, 끝나면 워커가 모두 종료할 때까지 기다려 뒤이은 정리 DELETE와 겹치지 않게 한다.
   */
  private Map<String, Integer> runConcurrently(List<Callable<String>> tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    Throwable failure = null;
    try {
      CountDownLatch ready = new CountDownLatch(Math.min(tasks.size(), THREADS));
      CountDownLatch start = new CountDownLatch(1);
      List<Future<String>> futures = new ArrayList<>();
      for (Callable<String> task : tasks) {
        futures.add(
            executor.submit(() -> {
              ready.countDown();
              start.await();
              return task.call();
            }));
      }
      assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("스레드 준비 시간 초과").isTrue();
      start.countDown();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

      Map<String, Integer> distribution = new TreeMap<>();
      int collected = 0;
      for (Future<String> future : futures) {
        long remaining = Math.max(0, deadline - System.nanoTime());
        try {
          distribution.merge(future.get(remaining, TimeUnit.NANOSECONDS), 1, Integer::sum);
          collected++;
        } catch (TimeoutException exception) {
          throw new AssertionError(
              "전체 마감 %d초 초과: 모인 분포 %s, 미완료 %d건 (전체 %d건)"
                  .formatted(TIMEOUT_SECONDS, distribution, tasks.size() - collected, tasks.size()),
              exception);
        }
      }
      return distribution;
    } catch (Throwable throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      executor.shutdownNow();
      boolean terminated = false;
      try {
        terminated = executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      if (!terminated) {
        AssertionError notTerminated =
            new AssertionError(
                "실행기의 워커가 %d초 안에 종료되지 않았다. 정리 DELETE와 겹칠 수 있다.".formatted(TIMEOUT_SECONDS));
        if (failure != null) {
          notTerminated.addSuppressed(failure);
        }
        throw notTerminated;
      }
    }
  }
}
