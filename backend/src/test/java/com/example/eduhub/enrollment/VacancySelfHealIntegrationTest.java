package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseCommandService;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.example.eduhub.course.SessionRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 자기 복구(FR-06 최종 검토 W1, D-26): FR-05 기간에 정원만 늘어 "확정 < 정원이고 대기자가 있는" 오래된 상태가 신청·취소·정원 변경 중 어느 경로로든
 * 풀리는지 실제 MariaDB로 확인한다. 클래스에 {@code @Transactional}을 붙이지 않아 서비스가 자기 트랜잭션으로 커밋한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class VacancySelfHealIntegrationTest {

  @Autowired private EnrollmentCommandService enrollmentCommandService;
  @Autowired private CourseCommandService courseCommandService;
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
      jdbcTemplate.update("DELETE FROM course_session WHERE id = ?", sessionId);
    }
    for (Long courseId : courseIds) {
      jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
    }
    sessionIds.clear();
    courseIds.clear();
  }

  /** SH1: 오래된 상태(확정 5 < 정원 8, 대기 2)에서 새 신청이 들어오면 대기자를 먼저 채우고 판정한다. */
  @Test
  void enrollFillsStaleVacanciesBeforeDeciding() {
    Long sessionId = givenStaleState(5, 2);

    var response = enrollmentCommandService.enroll(sessionId, "NEW1");

    assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(employees(sessionId, "CONFIRMED"))
        .as("DB 상태: %s", dump(sessionId))
        .containsExactly("E1", "E2", "E3", "E4", "E5", "E6", "E7", "NEW1");
    assertThat(employees(sessionId, "WAITING")).as("DB 상태: %s", dump(sessionId)).isEmpty();
  }

  /** SH2: 오래된 상태(확정 5 < 정원 8, 대기 4)에서 대기자 한 명이 취소하면 남은 대기 3명이 모두 확정된다. */
  @Test
  void waitingCancelFillsStaleVacancies() {
    Long sessionId = givenStaleState(5, 4);

    enrollmentCommandService.cancel(sessionId, "E7");

    assertThat(employees(sessionId, "CONFIRMED"))
        .as("DB 상태: %s", dump(sessionId))
        .containsExactly("E1", "E2", "E3", "E4", "E5", "E6", "E8", "E9");
    assertThat(employees(sessionId, "WAITING")).as("DB 상태: %s", dump(sessionId)).isEmpty();
    assertThat(employees(sessionId, "CANCELLED"))
        .as("DB 상태: %s", dump(sessionId))
        .containsExactly("E7");
  }

  /** SH3: 오래된 상태(확정 5 < 정원 8, 대기 2)에서 정원을 7로 줄이면 강등 없이 대기 2명이 확정된다. */
  @Test
  void capacityDecreaseFillsStaleVacanciesWithoutDemotion() {
    Long sessionId = givenStaleState(5, 2);
    CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();

    courseCommandService.updateSession(
        sessionId, new SessionRequest(session.getStartAt(), session.getPlace(), 7));

    assertThat(employees(sessionId, "CONFIRMED"))
        .as("DB 상태: %s", dump(sessionId))
        .containsExactly("E1", "E2", "E3", "E4", "E5", "E6", "E7");
    assertThat(employees(sessionId, "WAITING")).as("DB 상태: %s", dump(sessionId)).isEmpty();
  }

  /**
   * 정원 5인 회차에 확정 confirmed명, 대기 waiting명(사번 E1, E2, ... 순)을 서비스로 만든 뒤 JDBC로 정원만 8로 바꿔 오래된 상태를 만든다.
   */
  private Long givenStaleState(int confirmed, int waiting) {
    Course course = courseRepository.save(new Course("자기 복구 과정", "테스트", null));
    courseIds.add(course.getId());
    CourseSession session =
        courseSessionRepository.save(
            new CourseSession(course, Instant.now().plus(Duration.ofDays(7)), "본사", 5));
    Long sessionId = session.getId();
    sessionIds.add(sessionId);
    for (int i = 1; i <= confirmed + waiting; i++) {
      enrollmentCommandService.enroll(sessionId, "E" + i);
    }
    jdbcTemplate.update("UPDATE course_session SET capacity = 8 WHERE id = ?", sessionId);
    assertThat(employees(sessionId, "CONFIRMED"))
        .as("사전 상태: %s", dump(sessionId))
        .hasSize(confirmed);
    assertThat(employees(sessionId, "WAITING")).as("사전 상태: %s", dump(sessionId)).hasSize(waiting);
    return sessionId;
  }

  private List<String> employees(Long sessionId, String status) {
    return jdbcTemplate.queryForList(
        "SELECT employee_id FROM enrollment WHERE session_id = ? AND status = ?"
            + " ORDER BY LENGTH(employee_id), employee_id",
        String.class,
        sessionId,
        status);
  }

  private String dump(Long sessionId) {
    return jdbcTemplate
        .queryForList(
            "SELECT employee_id, status, waiting_key FROM enrollment WHERE session_id = ?"
                + " ORDER BY id",
            sessionId)
        .toString();
  }
}
