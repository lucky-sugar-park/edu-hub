package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.common.ConflictException;
import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseCommandService;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
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
  @Autowired private CourseCommandService courseCommandService;
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

  @Test
  @DisplayName("C5: 정원 1에 서로 다른 100명이 동시에 신청하면 대기 순번은 1~99로 중복·빈틈이 없다")
  void waitingNumbersAreUniqueAndContiguous() throws Exception {
    Long sessionId = givenSession(givenCourse(), 1);

    Queue<Integer> waitingNumbers = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      String employeeId = "C5-" + i;
      tasks.add(() -> {
        try {
          EnrollmentResponse response = enrollmentCommandService.enroll(sessionId, employeeId);
          if (response.waitingNumber() != null) {
            waitingNumbers.add(response.waitingNumber());
          }
          return response.status().name();
        } catch (ConflictException exception) {
          return exception.getCode();
        } catch (Throwable throwable) {
          return throwable.getClass().getName();
        }
      });
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    List<Long> keysInDb =
        jdbcTemplate.queryForList(
            "SELECT waiting_key FROM enrollment WHERE session_id = ? AND status = 'WAITING'"
                + " ORDER BY waiting_key",
            Long.class,
            sessionId);
    List<Integer> sortedNumbers = waitingNumbers.stream().sorted().toList();
    List<Integer> expected = IntStream.rangeClosed(1, 99).boxed().toList();
    assertThat(distribution)
        .as("결과 분포 %s, 응답 순번 %s, DB 대기 키 %s", distribution, sortedNumbers, keysInDb)
        .containsOnly(Map.entry("CONFIRMED", 1), Map.entry("WAITING", 99));
    assertThat(sortedNumbers)
        .as("응답 waitingNumber(정렬) %s, 분포 %s", sortedNumbers, distribution)
        .isEqualTo(expected);
    assertThat(keysInDb)
        .as("DB 대기 키 %s, 분포 %s", keysInDb, distribution)
        .hasSize(99)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("C6: 정원 감소와 신규 신청 50건이 섞여도 불변식 I1~I5가 성립하고 예외가 없다")
  void capacityDecreaseMixedWithNewApplications() throws Exception {
    Long sessionId = givenSession(givenCourse(), 10);
    for (int i = 0; i < 10; i++) {
      enrollmentCommandService.enroll(sessionId, "C6-PRE-" + i);
    }
    for (int i = 0; i < 5; i++) {
      enrollmentCommandService.enroll(sessionId, "C6-OLD-" + i);
    }
    CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();
    SessionRequest decrease = new SessionRequest(session.getStartAt(), session.getPlace(), 6);

    // 정원 변경이 신청들 사이(처음 출발하는 32개 안)에 섞이도록 중간에 끼운다.
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      if (i == 20) {
        tasks.add(() -> {
          try {
            courseCommandService.updateSession(sessionId, decrease);
            return "UPDATED";
          } catch (Throwable throwable) {
            return "UPDATE_" + throwable.getClass().getName();
          }
        });
      }
      tasks.add(enrollTask(sessionId, "C6-NEW-" + i));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT employee_id, status, waiting_key FROM enrollment WHERE session_id = ?"
                + " ORDER BY waiting_key, id",
            sessionId);
    List<String> confirmed = new ArrayList<>();
    List<String> waitingOrder = new ArrayList<>();
    List<Long> waitingKeys = new ArrayList<>();
    int nonWaitingWithKey = 0;
    for (Map<String, Object> row : rows) {
      String status = (String) row.get("status");
      Object key = row.get("waiting_key");
      if ("CONFIRMED".equals(status)) {
        confirmed.add((String) row.get("employee_id"));
      }
      if ("WAITING".equals(status)) {
        waitingOrder.add((String) row.get("employee_id"));
        waitingKeys.add(((Number) key).longValue());
      } else if (key != null) {
        nonWaitingWithKey++;
      }
    }
    Integer capacityInDb =
        jdbcTemplate.queryForObject(
            "SELECT capacity FROM course_session WHERE id = ?", Integer.class, sessionId);
    String state =
        "분포 %s, 정원 %d, 확정 %s, 대기 순서 %s, 대기 키 %s"
            .formatted(distribution, capacityInDb, confirmed, waitingOrder, waitingKeys);

    // 예외 0건: 정원 변경 1건 성공 + 신규 신청 50건 모두 대기(확정 10 > 새 정원 6이므로 빈 자리가 없다).
    assertThat(distribution)
        .as(state)
        .containsOnly(Map.entry("UPDATED", 1), Map.entry("WAITING", 50));
    assertThat(capacityInDb).as(state).isEqualTo(6);
    // I1: 확정 수는 정원 이하(정확히 앞 6명)
    assertThat(confirmed.size()).as("I1 " + state).isLessThanOrEqualTo(6);
    assertThat(confirmed)
        .as("I1 확정 6명은 먼저 확정된 PRE-0..5 " + state)
        .containsExactlyInAnyOrder(
            "C6-PRE-0", "C6-PRE-1", "C6-PRE-2", "C6-PRE-3", "C6-PRE-4", "C6-PRE-5");
    // I2: 대기 키 중복 없음, 비대기 행은 키 없음
    assertThat(waitingKeys).as("I2 대기 키 중복 " + state).doesNotHaveDuplicates();
    assertThat(nonWaitingWithKey).as("I2 비대기 행의 키 " + state).isZero();
    // I3: 키 정렬 기준 순번이 1..N 연속(대기 59명 = 강등 4 + 기존 5 + 신규 50)
    assertThat(waitingOrder).as("I3 대기 인원 " + state).hasSize(59);
    // I4: 강등자 4명(확정 순)이 기존 대기자 5명(신청 순)보다 앞
    assertThat(waitingOrder.subList(0, 9))
        .as("I4 강등자 → 기존 대기자 순서 " + state)
        .containsExactly(
            "C6-PRE-6",
            "C6-PRE-7",
            "C6-PRE-8",
            "C6-PRE-9",
            "C6-OLD-0",
            "C6-OLD-1",
            "C6-OLD-2",
            "C6-OLD-3",
            "C6-OLD-4");
    // I5: 신규 신청자 50명은 모두 기존 대기자 뒤
    List<String> expectedNew = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      expectedNew.add("C6-NEW-" + i);
    }
    assertThat(waitingOrder.subList(9, 59))
        .as("I5 신규 신청자는 모두 맨 뒤 " + state)
        .containsExactlyInAnyOrderElementsOf(expectedNew);
  }

  @Test
  @DisplayName("C6b: 정원 10·확정 3에서 정원 5 감소와 신규 신청 30건이 섞여도 확정은 5명 이하이고 예외가 없다")
  void capacityDecreaseWithNewApplicationsOnPartiallyFilledSession() throws Exception {
    Long sessionId = givenSession(givenCourse(), 10);
    for (int i = 0; i < 3; i++) {
      enrollmentCommandService.enroll(sessionId, "C6B-PRE-" + i);
    }
    CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();
    SessionRequest decrease = new SessionRequest(session.getStartAt(), session.getPlace(), 5);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      if (i == 10) {
        tasks.add(() -> {
          try {
            courseCommandService.updateSession(sessionId, decrease);
            return "UPDATED";
          } catch (Throwable throwable) {
            return "UPDATE_" + throwable.getClass().getName();
          }
        });
      }
      tasks.add(enrollTask(sessionId, "C6B-NEW-" + i));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    long confirmedInDb =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    List<Long> waitingKeys =
        jdbcTemplate.queryForList(
            "SELECT waiting_key FROM enrollment WHERE session_id = ? AND status = 'WAITING'",
            Long.class,
            sessionId);
    Integer capacityInDb =
        jdbcTemplate.queryForObject(
            "SELECT capacity FROM course_session WHERE id = ?", Integer.class, sessionId);
    String state =
        "분포 %s, 정원 %d, 확정 %d, 대기 키 %s"
            .formatted(distribution, capacityInDb, confirmedInDb, waitingKeys);

    // 예외 0건: 정원 변경 1건 성공, 신규 신청 30건은 모두 확정 또는 대기
    assertThat(distribution.keySet()).as(state).isSubsetOf("UPDATED", "CONFIRMED", "WAITING");
    assertThat(distribution.getOrDefault("UPDATED", 0)).as(state).isEqualTo(1);
    assertThat(capacityInDb).as(state).isEqualTo(5);
    // I1: 신청이 33건이라 정원이 차는 것이 정상이므로 확정은 정확히 5명
    assertThat(confirmedInDb).as("I1 " + state).isEqualTo(5);
    // 대기 키 중복 없음
    assertThat(waitingKeys).as("대기 키 중복 " + state).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("C7: 확정자 10명이 동시에 취소하면 대기 앞 10명이 키 순으로 승급하고 남은 대기 순번은 1~10이다")
  void tenConfirmedCancelConcurrently() throws Exception {
    Long sessionId = givenSession(givenCourse(), 10);
    enrollSequentially(sessionId, "C7-PRE-", 10);
    enrollSequentially(sessionId, "C7-OLD-", 20);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(cancelTask(sessionId, "C7-PRE-" + i));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    DbState db = readState(sessionId);
    String state = db.describe(distribution);
    assertThat(distribution).as(state).containsOnly(Map.entry("CANCELLED", 10));
    assertThat(db.confirmed)
        .as("확정은 원래 대기 앞 10명 " + state)
        .containsExactlyInAnyOrderElementsOf(names("C7-OLD-", 0, 10));
    assertThat(db.waitingOrder)
        .as("남은 대기 순서 " + state)
        .containsExactlyElementsOf(names("C7-OLD-", 10, 20));
    assertThat(db.waitingKeys).as("대기 키 중복 " + state).doesNotHaveDuplicates();
    assertThat(db.waitingRanks)
        .as("남은 대기 순번 " + state)
        .isEqualTo(IntStream.rangeClosed(1, 10).boxed().toList());
    assertThat(db.cancelled)
        .as("취소 " + state)
        .containsExactlyInAnyOrderElementsOf(names("C7-PRE-", 0, 10));
    assertThat(db.nonWaitingWithKey).as("비대기 행의 키 " + state).isZero();
  }

  @Test
  @DisplayName("C8: 확정자 취소 5건과 신규 신청 20건이 섞여도 확정은 정원 5이고 기존 대기자가 신규보다 앞선다")
  void cancelMixedWithNewApplications() throws Exception {
    Long sessionId = givenSession(givenCourse(), 5);
    enrollSequentially(sessionId, "C8-PRE-", 5);
    enrollSequentially(sessionId, "C8-OLD-", 10);

    // 취소가 신청들 사이(처음 출발하는 32개 안)에 섞이도록 번갈아 넣는다.
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      tasks.add(enrollTask(sessionId, "C8-NEW-" + i));
      if (i % 4 == 0) {
        tasks.add(cancelTask(sessionId, "C8-PRE-" + (i / 4)));
      }
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    DbState db = readState(sessionId);
    String state = db.describe(distribution);
    // 승급은 최대 5명이라 기존 대기자 5명 이상이 항상 남아 있으므로, 신규 신청은 모두 대기가 된다(D-21).
    assertThat(distribution)
        .as(state)
        .containsOnly(Map.entry("CANCELLED", 5), Map.entry("WAITING", 20));
    // I1·I6: 시작 전이고 대기가 남아 있으므로 확정 = 정원 5, 승급은 기존 대기 앞 5명
    assertThat(db.confirmed)
        .as("I1·I6 확정은 원래 대기 앞 5명 " + state)
        .containsExactlyInAnyOrderElementsOf(names("C8-OLD-", 0, 5));
    assertThat(db.waitingKeys).as("대기 키 중복 " + state).doesNotHaveDuplicates();
    assertThat(db.waitingOrder).as("대기 25명 " + state).hasSize(25);
    assertThat(db.waitingOrder.subList(0, 5))
        .as("기존 대기자 나머지 5명이 맨 앞 " + state)
        .containsExactlyElementsOf(names("C8-OLD-", 5, 10));
    assertThat(db.waitingOrder.subList(5, 25))
        .as("신규 20명이 뒤 " + state)
        .containsExactlyInAnyOrderElementsOf(names("C8-NEW-", 0, 20));
    assertThat(db.cancelled)
        .as("취소 " + state)
        .containsExactlyInAnyOrderElementsOf(names("C8-PRE-", 0, 5));
    assertThat(db.nonWaitingWithKey).as("비대기 행의 키 " + state).isZero();
  }

  @Test
  @DisplayName("C9: 정원 5->8 변경, 확정자 취소 3건, 신규 신청 10건이 섞여도 확정은 8이고 승급은 키 순이다")
  void capacityIncreaseMixedWithCancelAndApplications() throws Exception {
    Long sessionId = givenSession(givenCourse(), 5);
    enrollSequentially(sessionId, "C9-PRE-", 5);
    enrollSequentially(sessionId, "C9-OLD-", 10);
    CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();
    SessionRequest increase = new SessionRequest(session.getStartAt(), session.getPlace(), 8);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(enrollTask(sessionId, "C9-NEW-" + i));
      if (i == 2) {
        tasks.add(() -> {
          try {
            courseCommandService.updateSession(sessionId, increase);
            return "UPDATED";
          } catch (Throwable throwable) {
            return "UPDATE_" + throwable.getClass().getName();
          }
        });
      }
      if (i == 1 || i == 4 || i == 7) {
        tasks.add(cancelTask(sessionId, "C9-PRE-" + (i / 3)));
      }
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    DbState db = readState(sessionId);
    String state = db.describe(distribution);
    Integer capacityInDb =
        jdbcTemplate.queryForObject(
            "SELECT capacity FROM course_session WHERE id = ?", Integer.class, sessionId);
    // 승급은 취소 3 + 정원 증가 3 = 최대 6명이라 기존 대기 10명 중 4명이 항상 남아, 신규 신청은 모두 대기가 된다.
    assertThat(distribution)
        .as(state)
        .containsOnly(Map.entry("CANCELLED", 3), Map.entry("UPDATED", 1), Map.entry("WAITING", 10));
    assertThat(capacityInDb).as(state).isEqualTo(8);
    // I1·I6: 대기가 남아 있으므로 확정 = 정원 8 (남은 PRE 2 + 승급 6)
    assertThat(db.confirmed).as("I1·I6 확정 8 " + state).hasSize(8);
    assertThat(db.confirmed)
        .as("확정 구성 " + state)
        .containsExactlyInAnyOrder(
            "C9-PRE-3",
            "C9-PRE-4",
            "C9-OLD-0",
            "C9-OLD-1",
            "C9-OLD-2",
            "C9-OLD-3",
            "C9-OLD-4",
            "C9-OLD-5");
    // I7: 원래 대기자 중 확정되지 않은 사람은 모두 확정된 원래 대기자보다 키가 컸다
    long maxPromotedOld =
        db.confirmed.stream()
            .filter(e -> e.startsWith("C9-OLD-"))
            .mapToLong(e -> Long.parseLong(e.substring("C9-OLD-".length())))
            .max()
            .orElse(-1);
    assertThat(db.waitingOrder.stream().filter(e -> e.startsWith("C9-OLD-")).toList())
        .as("I7 남은 원래 대기자 " + state)
        .allSatisfy(e ->
            assertThat(Long.parseLong(e.substring("C9-OLD-".length())))
                .isGreaterThan(maxPromotedOld));
    assertThat(db.waitingKeys).as("대기 키 중복 " + state).doesNotHaveDuplicates();
    assertThat(db.waitingOrder).as("대기 14명 " + state).hasSize(14);
    assertThat(db.waitingOrder.subList(0, 4))
        .as("원래 대기 나머지 4명이 신규보다 앞 " + state)
        .containsExactlyElementsOf(names("C9-OLD-", 6, 10));
    assertThat(db.cancelled)
        .as("취소 " + state)
        .containsExactlyInAnyOrder("C9-PRE-0", "C9-PRE-1", "C9-PRE-2");
    assertThat(db.nonWaitingWithKey).as("비대기 행의 키 " + state).isZero();
  }

  @Test
  @DisplayName("C10: 같은 확정자의 취소 5건이 동시에 오면 1건만 성공하고 승급은 대기 1순위 1명뿐이다")
  void sameConfirmedCancelFiveTimes() throws Exception {
    Long sessionId = givenSession(givenCourse(), 3);
    enrollSequentially(sessionId, "C10-PRE-", 3);
    enrollSequentially(sessionId, "C10-OLD-", 2);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      tasks.add(cancelTask(sessionId, "C10-PRE-0"));
    }
    Map<String, Integer> distribution = runConcurrently(tasks);

    DbState db = readState(sessionId);
    String state = db.describe(distribution);
    assertThat(distribution)
        .as(state)
        .containsOnly(Map.entry("CANCELLED", 1), Map.entry("NOT_FOUND", 4));
    assertThat(db.confirmed)
        .as("확정 3 (승급은 대기 1순위만) " + state)
        .containsExactlyInAnyOrder("C10-PRE-1", "C10-PRE-2", "C10-OLD-0");
    assertThat(db.waitingOrder).as("대기 " + state).containsExactly("C10-OLD-1");
    assertThat(db.cancelled).as("취소 " + state).containsExactly("C10-PRE-0");
    assertThat(db.nonWaitingWithKey).as("비대기 행의 키 " + state).isZero();
  }

  private void enrollSequentially(Long sessionId, String prefix, int count) {
    for (int i = 0; i < count; i++) {
      enrollmentCommandService.enroll(sessionId, prefix + i);
    }
  }

  private static List<String> names(String prefix, int from, int toExclusive) {
    return IntStream.range(from, toExclusive).mapToObj(i -> prefix + i).toList();
  }

  /** 취소 성공이면 CANCELLED, 실패면 ConflictException code, NOT_FOUND, 또는 예외 클래스 이름을 돌려준다. */
  private Callable<String> cancelTask(Long sessionId, String employeeId) {
    return () -> {
      try {
        enrollmentCommandService.cancel(sessionId, employeeId);
        return "CANCELLED";
      } catch (ConflictException exception) {
        return exception.getCode();
      } catch (EnrollmentNotFoundException exception) {
        return "NOT_FOUND";
      } catch (Throwable throwable) {
        return throwable.getClass().getName();
      }
    };
  }

  /** DB에서 읽은 회차의 신청 상태. waitingRanks는 "키가 더 작은 대기자 수 + 1"을 SQL로 센 순번이다. */
  private record DbState(
      List<String> confirmed,
      List<String> waitingOrder,
      List<Long> waitingKeys,
      List<Integer> waitingRanks,
      List<String> cancelled,
      int nonWaitingWithKey) {

    String describe(Map<String, Integer> distribution) {
      return "분포 %s, 확정 %s, 대기 순서 %s, 대기 키 %s, 대기 순번 %s, 취소 %s"
          .formatted(distribution, confirmed, waitingOrder, waitingKeys, waitingRanks, cancelled);
    }
  }

  private DbState readState(Long sessionId) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT employee_id, status, waiting_key FROM enrollment WHERE session_id = ?"
                + " ORDER BY waiting_key, id",
            sessionId);
    List<String> confirmed = new ArrayList<>();
    List<String> waitingOrder = new ArrayList<>();
    List<Long> waitingKeys = new ArrayList<>();
    List<Integer> waitingRanks = new ArrayList<>();
    List<String> cancelled = new ArrayList<>();
    int nonWaitingWithKey = 0;
    for (Map<String, Object> row : rows) {
      String status = (String) row.get("status");
      String employeeId = (String) row.get("employee_id");
      Object key = row.get("waiting_key");
      if ("CONFIRMED".equals(status)) {
        confirmed.add(employeeId);
      } else if ("CANCELLED".equals(status)) {
        cancelled.add(employeeId);
      }
      if ("WAITING".equals(status)) {
        long waitingKey = ((Number) key).longValue();
        waitingOrder.add(employeeId);
        waitingKeys.add(waitingKey);
        Integer smaller =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE session_id = ? AND status = 'WAITING'"
                    + " AND waiting_key < ?",
                Integer.class,
                sessionId,
                waitingKey);
        waitingRanks.add(smaller + 1);
      } else if (key != null) {
        nonWaitingWithKey++;
      }
    }
    return new DbState(
        confirmed, waitingOrder, waitingKeys, waitingRanks, cancelled, nonWaitingWithKey);
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
