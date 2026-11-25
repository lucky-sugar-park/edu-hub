package com.example.eduhub.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 보안 필터 체인 통합 테스트(NFR-03, D-33). 실제 서명 토큰을 {@link TokenIssuer}로 발급한다. 토큰만 쓰므로 employee 행은 만들지 않는다.
 * 사번·과정 이름에 접두어 {@code S3-}를 써서 자기 데이터만 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

  private static final String PREFIX = "S3-";
  private static final String ADMIN_NO = "S3-ADMIN";
  private static final String EMPLOYEE_NO = "S3-E1";
  private static final String OTHER_NO = "S3-E2";

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenIssuer tokenIssuer;
  @Autowired private SecretKey jwtSecretKey;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long courseId;
  private Long sessionId;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM notification WHERE employee_id LIKE ?", PREFIX + "%");
    jdbcTemplate.update("DELETE FROM enrollment WHERE employee_id LIKE ?", PREFIX + "%");
    if (sessionId != null) {
      jdbcTemplate.update("DELETE FROM enrollment WHERE session_id = ?", sessionId);
      jdbcTemplate.update("DELETE FROM course_session WHERE id = ?", sessionId);
    }
    if (courseId != null) {
      jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
    }
    jdbcTemplate.update(
        "DELETE FROM course_session WHERE course_id IN (SELECT id FROM course WHERE title LIKE ?)",
        PREFIX + "%");
    jdbcTemplate.update("DELETE FROM course WHERE title LIKE ?", PREFIX + "%");
  }

  // A5
  @Test
  void enrollWithoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @Test
  void meWithoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void malformedTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A5
  @Test
  void expiredTokenIsUnauthorized() throws Exception {
    Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
    String token = sign(jwtSecretKey, "3", EMPLOYEE_NO, "EMPLOYEE", past, past.plusSeconds(60));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A5
  @Test
  void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
    byte[] other = new byte[32];
    new SecureRandom().nextBytes(other);
    Instant now = Instant.now();
    String token =
        sign(
            new SecretKeySpec(other, "HmacSHA256"),
            "3",
            EMPLOYEE_NO,
            "EMPLOYEE",
            now,
            now.plusSeconds(600));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A9
  @Test
  void courseRegistrationIs401WithoutToken403ForEmployeeAnd201ForAdmin() throws Exception {
    String body = "{\"title\":\"S3-보안 과정\",\"category\":\"테스트\"}";

    mockMvc
        .perform(
            post("/api/v1/admin/courses").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .header("Authorization", bearer(2L, EMPLOYEE_NO, EmployeeRole.EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").isNotEmpty());
    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .header("Authorization", bearer(1L, ADMIN_NO, EmployeeRole.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  // 과정 쓰기 방어 규칙: /api/v1/courses 아래 GET 외 요청과 /api/admin/** 은 관리자만 쓴다.
  @Test
  void courseWritePathsAndAdminPrefixAreAdminOnly() throws Exception {
    String employee = bearer(2L, EMPLOYEE_NO, EmployeeRole.EMPLOYEE);

    mockMvc
        .perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/courses")
                .header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(delete("/api/v1/courses/1").header("Authorization", employee))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/anything").header("Authorization", employee))
        .andExpect(status().isForbidden());
  }

  // 대조: 공개 GET 규칙이 ADMIN 규칙보다 앞이므로 토큰 없는 GET은 보안을 통과해 404(없는 과정)가 된다.
  @Test
  void publicCourseGetPrecedesAdminRule() throws Exception {
    mockMvc
        .perform(get("/api/v1/courses/999999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // A10: 관리자 API(명단 조회)에 사원 토큰은 403, 관리자 토큰은 통과한다. role 클레임이 ROLE_ADMIN으로 바뀌는지 실제 데이터로 확인한다.
  @Test
  void adminRosterIsForbiddenForEmployeeAndOkForAdmin() throws Exception {
    createSession();
    String roster = "/api/v1/admin/sessions/" + sessionId + "/roster";

    mockMvc
        .perform(get(roster).header("Authorization", bearer(1L, ADMIN_NO, EmployeeRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(sessionId))
        .andExpect(jsonPath("$.capacity").value(5));
    mockMvc
        .perform(
            get(roster).header("Authorization", bearer(2L, EMPLOYEE_NO, EmployeeRole.EMPLOYEE)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void adminApiWithoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A11
  @Test
  void publicEndpointsNeedNoLogin() throws Exception {
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  // A6: 토큰의 클레임으로 만든 응답이 오고, 이 경로는 DB를 조회하지 않는다(employee 행이 없다).
  @Test
  void meReturnsUserFromToken() throws Exception {
    mockMvc
        .perform(
            get("/api/me").header("Authorization", bearer(7L, EMPLOYEE_NO, EmployeeRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.employeeNo").value(EMPLOYEE_NO))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  // 신청자는 토큰의 사번이다(A7 일부). 로그인 경로를 포함한 전체 시나리오는 T5의 AuthIntegrationTest가 맡는다.
  @Test
  void enrollUsesEmployeeNoFromToken() throws Exception {
    createSession();
    String token = bearer(5L, EMPLOYEE_NO, EmployeeRole.EMPLOYEE);

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments").header("Authorization", token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_NO))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    mockMvc
        .perform(
            get("/api/v1/sessions/" + sessionId + "/enrollments/me").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_NO));
  }

  // 다른 사원의 토큰으로는 내 신청을 취소할 수 없다(자기 활성 신청이 없어 404). 위조 시도는 무시된다.
  @Test
  void otherEmployeeCannotCancelMyEnrollment() throws Exception {
    createSession();
    String mine = bearer(5L, EMPLOYEE_NO, EmployeeRole.EMPLOYEE);
    String other = bearer(6L, OTHER_NO, EmployeeRole.EMPLOYEE);
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments").header("Authorization", mine))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments/me/cancel")
                .param("employeeId", EMPLOYEE_NO)
                .header("X-Employee-Id", EMPLOYEE_NO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"" + EMPLOYEE_NO + "\"}")
                .header("Authorization", other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    mockMvc
        .perform(
            get("/api/v1/sessions/" + sessionId + "/enrollments/me").header("Authorization", mine))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments/me/cancel")
                .header("Authorization", mine))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  // 토큰에 사용자 클레임이 없으면 인증 정보가 없는 것으로 보고 401이다.
  @Test
  void tokenWithoutUserClaimsIsUnauthorizedOnEnroll() throws Exception {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("eduhub")
            .subject("3")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .claim("role", "EMPLOYEE")
            .build();
    String token =
        new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // 구 토큰(roles 클레임)은 권한이 없어 관리자 API에서 403이다.
  @Test
  void legacyRolesClaimGivesNoAuthority() throws Exception {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("eduhub-dev")
            .subject("E1001")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .claim("roles", java.util.List.of("EMPLOYEE", "ADMIN"))
            .build();
    String token =
        new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  // 공개 경로라도 만료·위조 토큰이 오면 401이다. 프런트 인터셉터가 처리한다.
  @Test
  void expiredTokenOnPublicPathIsUnauthorizedButNoTokenIsOk() throws Exception {
    Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
    String token = sign(jwtSecretKey, "3", EMPLOYEE_NO, "EMPLOYEE", past, past.plusSeconds(60));

    mockMvc
        .perform(get("/api/v1/courses").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
  }

  // alg=none 토큰은 거부한다.
  @Test
  void algNoneTokenIsUnauthorized() throws Exception {
    String token = compact("{\"alg\":\"none\"}", payload(Instant.now().plusSeconds(600))) + ".";

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }

  // 같은 키로 HS512 서명해도 HS256만 허용하므로 거부한다.
  @Test
  void hs512TokenSignedWithSameKeyIsUnauthorized() throws Exception {
    String signingInput = compact("{\"alg\":\"HS512\"}", payload(Instant.now().plusSeconds(600)));
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(jwtSecretKey.getEncoded(), "HmacSHA512"));
    String signature =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + signingInput + "." + signature))
        .andExpect(status().isUnauthorized());
  }

  // clock skew(기본 60초) 밖으로 만료된 토큰은 거부한다.
  @Test
  void tokenExpiredBeyondClockSkewIsUnauthorized() throws Exception {
    Instant now = Instant.now();
    String token =
        sign(
            jwtSecretKey,
            "3",
            EMPLOYEE_NO,
            "EMPLOYEE",
            now.minus(10, ChronoUnit.MINUTES),
            now.minus(2, ChronoUnit.MINUTES));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  private void createSession() {
    Course course = courseRepository.save(new Course("S3-보안 통합 테스트 과정", "테스트", "삭제될 데이터"));
    courseId = course.getId();
    CourseSession session =
        sessionRepository.save(
            new CourseSession(course, Instant.now().plus(30, ChronoUnit.DAYS), "테스트실", 5));
    sessionId = session.getId();
  }

  private static String payload(Instant expiresAt) {
    return "{\"sub\":\"3\",\"employee_no\":\""
        + EMPLOYEE_NO
        + "\",\"role\":\"EMPLOYEE\",\"exp\":"
        + expiresAt.getEpochSecond()
        + "}";
  }

  private static String compact(String headerJson, String payloadJson) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
        + "."
        + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
  }

  /** 실제 {@link TokenIssuer}로 발급한 토큰의 Authorization 헤더 값. */
  private String bearer(long id, String employeeNo, EmployeeRole role) {
    Employee employee = new Employee(employeeNo, "{bcrypt}unused", role, Instant.now());
    ReflectionTestUtils.setField(employee, "id", id);
    return "Bearer " + tokenIssuer.issue(employee).token();
  }

  private static String sign(
      SecretKey key,
      String subject,
      String employeeNo,
      String role,
      Instant issuedAt,
      Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("eduhub")
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("employee_no", employeeNo)
            .claim("role", role)
            .build();
    return new NimbusJwtEncoder(new ImmutableSecret<>(key))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
