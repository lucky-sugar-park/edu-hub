package com.example.eduhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseCommandService;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionRequest;
import com.example.eduhub.enrollment.EnrollmentCommandService;
import com.example.eduhub.enrollment.EnrollmentConfirmedEvent;
import com.example.eduhub.enrollment.EnrollmentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 기록 통합 테스트(FR-09, D-32). 실제 MariaDB에서 커밋이 일어나야 하므로 클래스에 {@code @Transactional}을 붙이지 않는다. 알림에는
 * 회차 id가 없어 테스트 사번에 접두어 {@code N9-}를 쓰고 그 사번의 알림만 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest {

  private static final String PREFIX = "N9-";
  private static final String MARKER_TITLE = "N9-롤백 표식 과정";

  @Autowired private EnrollmentCommandService enrollmentCommandService;
  @Autowired private CourseCommandService courseCommandService;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository courseSessionRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<Long> sessionIds = new ArrayList<>();
  private final List<Long> courseIds = new ArrayList<>();

  /** 이 테스트가 만든 신청, 회차, 과정, 그리고 사번 접두어 N9-의 알림만 지운다. */
  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM notification WHERE employee_id LIKE ?", PREFIX + "%");
    for (Long sessionId : sessionIds) {
      jdbcTemplate.update("DELETE FROM enrollment WHERE session_id = ?", sessionId);
      jdbcTemplate.update("DELETE FROM course_session WHERE id = ?", sessionId);
    }
    for (Long courseId : courseIds) {
      jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
    }
    jdbcTemplate.update("DELETE FROM course WHERE title = ?", MARKER_TITLE);
    sessionIds.clear();
    courseIds.clear();
  }

  /** N1: 빈 회차에 신청하면 확정되고 알림이 1건 남는다. */
  @Test
  void confirmedEnrollmentRecordsNotification() {
    Long sessionId = givenSession(5);

    enrollmentCommandService.enroll(sessionId, "N9-A");

    List<Notification> notifications = notificationRepository.findByEmployeeIdOrderByIdAsc("N9-A");
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.ENROLLMENT_CONFIRMED);
    assertThat(notifications.get(0).getMessage()).contains(String.valueOf(sessionId));
  }

  /** N2: 확정 신청 뒤 같은 트랜잭션이 예외로 롤백되면 신청도 알림도 남지 않는다. */
  @Test
  void rolledBackEnrollmentLeavesNoNotification() {
    Long sessionId = givenSession(5);

    assertThatThrownBy(() ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                  // 바깥 트랜잭션은 기본 격리 수준이라 enroll의 READ_COMMITTED는 적용되지 않는다. 이 테스트는 격리 수준을 검증하지 않는다.
                  var response = enrollmentCommandService.enroll(sessionId, "N9-A");
                  assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
                  assertThat(enrollmentCount(sessionId)).isEqualTo(1);
                  throw new IllegalStateException("강제 롤백");
                }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("강제 롤백");

    assertThat(enrollmentCount(sessionId)).isZero();
    assertThat(notificationRepository.findByEmployeeIdOrderByIdAsc("N9-A")).isEmpty();
  }

  /** N3: 정원이 찬 회차의 신청은 대기이고 알림이 없다. */
  @Test
  void waitingEnrollmentRecordsNoNotification() {
    Long sessionId = givenSession(1);
    enrollmentCommandService.enroll(sessionId, "N9-A");

    var response = enrollmentCommandService.enroll(sessionId, "N9-B");

    assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITING);
    assertThat(notificationRepository.findByEmployeeIdOrderByIdAsc("N9-B")).isEmpty();
  }

  /** N4: 확정자가 취소하면 대기 1순위가 승급하고 승급 알림이 1건 남는다. 취소자는 신청 때의 1건뿐이다. */
  @Test
  void cancelPromotesWaitingAndRecordsNotification() {
    Long sessionId = givenSession(1);
    enrollmentCommandService.enroll(sessionId, "N9-A");
    enrollmentCommandService.enroll(sessionId, "N9-B");

    enrollmentCommandService.cancel(sessionId, "N9-A");

    List<Notification> promoted = notificationRepository.findByEmployeeIdOrderByIdAsc("N9-B");
    assertThat(promoted).hasSize(1);
    assertThat(promoted.get(0).getType()).isEqualTo(NotificationType.WAITING_PROMOTED);
    assertThat(promoted.get(0).getMessage()).contains(String.valueOf(sessionId));
    List<Notification> cancelled = notificationRepository.findByEmployeeIdOrderByIdAsc("N9-A");
    assertThat(cancelled).hasSize(1);
    assertThat(cancelled.get(0).getType()).isEqualTo(NotificationType.ENROLLMENT_CONFIRMED);
  }

  /** N5: 정원을 늘리면 대기 2명이 승급하고 각각 알림이 1건 남는다. */
  @Test
  void capacityIncreasePromotesWaitingAndRecordsNotifications() {
    Long sessionId = givenSession(1);
    enrollmentCommandService.enroll(sessionId, "N9-A");
    enrollmentCommandService.enroll(sessionId, "N9-B");
    enrollmentCommandService.enroll(sessionId, "N9-C");
    CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();

    courseCommandService.updateSession(
        sessionId, new SessionRequest(session.getStartAt(), session.getPlace(), 3));

    for (String employeeId : List.of("N9-B", "N9-C")) {
      List<Notification> notifications =
          notificationRepository.findByEmployeeIdOrderByIdAsc(employeeId);
      assertThat(notifications).as(employeeId).hasSize(1);
      assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.WAITING_PROMOTED);
    }
    assertThat(notificationRepository.findByEmployeeIdOrderByIdAsc("N9-A")).hasSize(1);
  }

  /**
   * N6(대체): 오래된 상태(확정 1 < 정원 4, 대기 2)에서 신청이 자기 복구로 대기자를 승급시킨 뒤 같은 트랜잭션이 롤백되면 승급 알림이 없고 대기자도 그대로
   * 대기다. 중복 검사가 자기 복구보다 앞이라 실제 409는 복구 뒤에 날 수 없어 TransactionTemplate 롤백으로 대체했다.
   */
  @Test
  void rollbackAfterSelfHealDiscardsPromotionNotifications() {
    Long sessionId = givenSession(1);
    enrollmentCommandService.enroll(sessionId, "N9-A");
    enrollmentCommandService.enroll(sessionId, "N9-B");
    enrollmentCommandService.enroll(sessionId, "N9-C");
    jdbcTemplate.update("UPDATE course_session SET capacity = 4 WHERE id = ?", sessionId);
    assertThat(employees(sessionId, "WAITING")).containsExactly("N9-B", "N9-C");

    assertThatThrownBy(() ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                  // 바깥 트랜잭션은 기본 격리 수준이라 enroll의 READ_COMMITTED는 적용되지 않는다. 이 테스트는 격리 수준을 검증하지 않는다.
                  enrollmentCommandService.enroll(sessionId, "N9-D");
                  // 자기 복구 승급이 실제로 일어났음을 같은 커넥션에서 확인한다.
                  assertThat(employees(sessionId, "CONFIRMED"))
                      .containsExactly("N9-A", "N9-B", "N9-C", "N9-D");
                  assertThat(employees(sessionId, "WAITING")).isEmpty();
                  throw new IllegalStateException("강제 롤백");
                }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("강제 롤백");

    assertThat(employees(sessionId, "WAITING")).containsExactly("N9-B", "N9-C");
    assertThat(employees(sessionId, "CONFIRMED")).containsExactly("N9-A");
    for (String employeeId : List.of("N9-B", "N9-C", "N9-D")) {
      assertThat(notificationRepository.findByEmployeeIdOrderByIdAsc(employeeId))
          .as(employeeId)
          .isEmpty();
    }
  }

  /**
   * N9: 알림 저장이 실패하면(사번 21자, VARCHAR(20) 초과) 바깥 트랜잭션도 롤백된다. 같은 트랜잭션에서 JDBC로 넣은 표식 과정 행이 남지 않아야 롤백이
   * 증명된다.
   */
  @Test
  void notificationSaveFailureRollsBackOuterTransaction() {
    String tooLong = "N9-" + "X".repeat(18);
    assertThat(tooLong).hasSize(21);

    assertThatThrownBy(() ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                  jdbcTemplate.update(
                      "INSERT INTO course (title, category, status) VALUES (?, ?, 'OPEN')",
                      MARKER_TITLE,
                      "테스트");
                  assertThat(markerCount()).isEqualTo(1);
                  eventPublisher.publishEvent(
                      new EnrollmentConfirmedEvent(
                          1L, tooLong, 1L, EnrollmentConfirmedEvent.Reason.APPLIED));
                }))
        .isInstanceOf(DataIntegrityViolationException.class)
        .rootCause()
        .hasMessageContaining("Data too long");

    assertThat(markerCount()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE employee_id LIKE ?",
                Long.class,
                PREFIX + "%"))
        .isZero();
  }

  private Long givenSession(int capacity) {
    Course course = courseRepository.save(new Course("알림 통합 테스트 과정", "테스트", null));
    courseIds.add(course.getId());
    CourseSession session =
        courseSessionRepository.save(
            new CourseSession(course, Instant.now().plus(Duration.ofDays(7)), "본사", capacity));
    sessionIds.add(session.getId());
    return session.getId();
  }

  private long markerCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM course WHERE title = ?", Long.class, MARKER_TITLE);
  }

  private long enrollmentCount(Long sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM enrollment WHERE session_id = ?", Long.class, sessionId);
  }

  private List<String> employees(Long sessionId, String status) {
    return jdbcTemplate.queryForList(
        "SELECT employee_id FROM enrollment WHERE session_id = ? AND status = ?"
            + " ORDER BY employee_id",
        String.class,
        sessionId,
        status);
  }
}
