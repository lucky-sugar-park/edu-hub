package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 로그인부터 신청·취소까지 실제 DB와 실제 서명으로 확인한다(NFR-03, D-33). 커밋형 테스트이므로 사번·과정 이름에 접두어 {@code T5-}를 쓰고 끝에
 * 자기 데이터만 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

  private static final String PREFIX = "T5-";
  private static final String NO_A = "T5-A";
  private static final String NO_B = "T5-B";
  private static final String NO_ADMIN = "T5-ADMIN";
  private static final String PASSWORD = "t5-secret-pw";

  @Autowired private MockMvc mockMvc;
  @Autowired private EmployeeRepository employeeRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Employee employeeA;
  private Employee employeeB;
  private Employee admin;
  private Long sessionId;

  @BeforeEach
  void createAccounts() {
    cleanUp();
    Instant now = Instant.now();
    String hash = passwordEncoder.encode(PASSWORD);
    employeeA = employeeRepository.save(new Employee(NO_A, hash, EmployeeRole.EMPLOYEE, now));
    employeeB = employeeRepository.save(new Employee(NO_B, hash, EmployeeRole.EMPLOYEE, now));
    admin = employeeRepository.save(new Employee(NO_ADMIN, hash, EmployeeRole.ADMIN, now));
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM notification WHERE employee_id LIKE ?", PREFIX + "%");
    jdbcTemplate.update("DELETE FROM enrollment WHERE employee_id LIKE ?", PREFIX + "%");
    jdbcTemplate.update(
        "DELETE FROM enrollment WHERE session_id IN (SELECT s.id FROM course_session s"
            + " JOIN course c ON c.id = s.course_id WHERE c.title LIKE ?)",
        PREFIX + "%");
    jdbcTemplate.update(
        "DELETE FROM course_session WHERE course_id IN (SELECT id FROM course WHERE title LIKE ?)",
        PREFIX + "%");
    jdbcTemplate.update("DELETE FROM course WHERE title LIKE ?", PREFIX + "%");
    jdbcTemplate.update("DELETE FROM employee WHERE employee_no LIKE ?", PREFIX + "%");
  }

  // A1
  @Test
  void loginIssuesTokenWithIdRoleAndEmployeeNoClaims() throws Exception {
    String body =
        login(NO_ADMIN, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andExpect(jsonPath("$.user.id").value(admin.getId()))
            .andExpect(jsonPath("$.user.employeeNo").value(NO_ADMIN))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
    assertThat(jwt.getSubject()).isEqualTo(String.valueOf(admin.getId()));
    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    assertThat(jwt.getClaimAsString("employee_no")).isEqualTo(NO_ADMIN);
    assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
  }

  @Test
  void tokenFromLoginWorksOnMeEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + loginToken(NO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(employeeA.getId()))
        .andExpect(jsonPath("$.employeeNo").value(NO_A))
        .andExpect(jsonPath("$.role").value("EMPLOYEE"));
  }

  // A2, A3: 상태와 본문이 완전히 같아 사번 존재 여부가 드러나지 않는다.
  @Test
  void wrongPasswordAndUnknownEmployeeGiveIdenticalResponses() throws Exception {
    String wrongPassword =
        login(NO_A, "wrong-password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("사번 또는 비밀번호가 올바르지 않습니다"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownEmployee =
        login("T5-NOBODY", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(unknownEmployee).isEqualTo(wrongPassword);
  }

  // A4
  @Test
  void blankEmployeeNoAndPasswordAreBadRequest() throws Exception {
    login("", PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    login(NO_A, "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    login(" ", " ").andExpect(status().isBadRequest());
  }

  // A7: 로그인 토큰으로 신청하면 enrollment.employee_id는 그 사원의 사번이다.
  @Test
  void enrollmentIsStoredWithEmployeeNoOfLoggedInEmployee() throws Exception {
    createSession();
    String token = loginToken(NO_A);

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employeeId").value(NO_A));

    String stored =
        jdbcTemplate.queryForObject(
            "SELECT employee_id FROM enrollment WHERE session_id = ?", String.class, sessionId);
    assertThat(stored).isEqualTo(NO_A);
  }

  // A8: 사원 B의 토큰으로는 사원 A만 신청한 회차를 취소할 수 없고, A의 신청은 그대로다.
  @Test
  void otherEmployeeCannotCancelEnrollmentOfAnother() throws Exception {
    createSession();
    String tokenA = loginToken(NO_A);
    String tokenB = loginToken(NO_B);
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments/me/cancel")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM enrollment WHERE session_id = ? AND employee_id = ?",
            String.class,
            sessionId,
            NO_A);
    assertThat(status).isEqualTo("CONFIRMED");
  }

  private void createSession() {
    Course course = courseRepository.save(new Course("T5-로그인 통합 테스트 과정", "테스트", "삭제될 데이터"));
    CourseSession session =
        sessionRepository.save(
            new CourseSession(course, Instant.now().plus(30, ChronoUnit.DAYS), "테스트실", 5));
    sessionId = session.getId();
  }

  private ResultActions login(String employeeNo, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"employeeNo\":\"" + employeeNo + "\",\"password\":\"" + password + "\"}"));
  }

  private String loginToken(String employeeNo) throws Exception {
    String body =
        login(employeeNo, PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }
}
