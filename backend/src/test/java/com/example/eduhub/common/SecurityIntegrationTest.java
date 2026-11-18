package com.example.eduhub.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.course.Course;
import com.example.eduhub.course.CourseRepository;
import com.example.eduhub.course.CourseSession;
import com.example.eduhub.course.CourseSessionRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 보안 필터 체인과 dev 로그인(NFR-03, D-30) 통합 테스트. 설계 8.1의 A1~A6. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// OS 환경 변수(EDUHUB_ADMIN_EMPLOYEE_IDS)가 yml 값을 덮을 수 있으므로 관리자 목록을 고정한다.
@TestPropertySource(properties = "eduhub.admin.employee-ids=TEST-ADMIN")
class SecurityIntegrationTest {

  private static final String ADMIN_ID = "TEST-ADMIN";
  private static final String EMPLOYEE_ID = "E1001";

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
    if (sessionId != null) {
      jdbcTemplate.update("DELETE FROM enrollment WHERE session_id = ?", sessionId);
      jdbcTemplate.update("DELETE FROM course_session WHERE id = ?", sessionId);
    }
    if (courseId != null) {
      jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
    }
  }

  // A1
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
  void malformedTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A2
  @Test
  void expiredTokenIsUnauthorized() throws Exception {
    Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
    String token = sign(jwtSecretKey, EMPLOYEE_ID, List.of("EMPLOYEE"), past, past.plusSeconds(60));

    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A2
  @Test
  void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
    byte[] other = new byte[32];
    new SecureRandom().nextBytes(other);
    Instant now = Instant.now();
    String token =
        sign(
            new SecretKeySpec(other, "HmacSHA256"),
            EMPLOYEE_ID,
            List.of("EMPLOYEE"),
            now,
            now.plusSeconds(600));

    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A3
  @Test
  void employeeTokenGetsForbiddenOnAdminApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .header("Authorization", bearer(EMPLOYEE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @Test
  void adminApiWithoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // A4: 보안은 통과하고 본문 검증(400)에서 멈춘다. 데이터를 만들지 않는다.
  @Test
  void adminTokenPassesSecurityOnAdminApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/courses")
                .header("Authorization", bearer(ADMIN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  // A5
  @Test
  void publicEndpointsNeedNoLogin() throws Exception {
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  // A6
  @Test
  void devLoginIssuesEmployeeRolesForOrdinaryEmployee() throws Exception {
    login(EMPLOYEE_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
        .andExpect(jsonPath("$.roles.length()").value(1))
        .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());
  }

  // A6
  @Test
  void devLoginAddsAdminRoleForListedEmployee() throws Exception {
    login(ADMIN_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
        .andExpect(jsonPath("$.roles[1]").value("ADMIN"));
  }

  @Test
  void devLoginRejectsBlankEmployeeId() throws Exception {
    login(" ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  // A6: dev 로그인으로 받은 토큰만으로 신청한다. 사번은 토큰의 sub에서 읽는다(D-30).
  @Test
  void tokenFromDevLoginEnrollsWithEmployeeIdFromToken() throws Exception {
    createSession();
    String token = loginToken(EMPLOYEE_ID);

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    mockMvc
        .perform(
            get("/api/v1/sessions/" + sessionId + "/enrollments/me")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID));
  }

  // A9: 다른 사원의 토큰으로는 내 신청을 취소할 수 없다(자기 활성 신청이 없어 404).
  // 위조 시도(쿼리 파라미터, 폐지된 헤더 X-Employee-Id, 본문)를 함께 보내도 무시되는지 확인한다.
  @Test
  void otherEmployeeCannotCancelMyEnrollment() throws Exception {
    createSession();
    String mine = loginToken(EMPLOYEE_ID);
    String other = loginToken("E2002");
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments")
                .header("Authorization", "Bearer " + mine))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments/me/cancel")
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    // 폐지된 헤더·쿼리·본문이 무시되는지 확인
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments/me/cancel")
                .param("employeeId", EMPLOYEE_ID)
                .header("X-Employee-Id", EMPLOYEE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"" + EMPLOYEE_ID + "\"}")
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    // 내 신청은 그대로 활성이고, 내가 취소하면 취소된다.
    mockMvc
        .perform(
            get("/api/v1/sessions/" + sessionId + "/enrollments/me")
                .header("Authorization", "Bearer " + mine))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments/me/cancel")
                .header("Authorization", "Bearer " + mine))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  // 폐지된 헤더·쿼리·본문이 무시되는지 확인: 신청자는 항상 토큰의 사번이다.
  @Test
  void enrollIgnoresForgedEmployeeIdInHeaderQueryAndBody() throws Exception {
    createSession();
    String other = loginToken("E2002");

    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments")
                .param("employeeId", EMPLOYEE_ID)
                .header("X-Employee-Id", EMPLOYEE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"" + EMPLOYEE_ID + "\"}")
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employeeId").value("E2002"));

    // E1001로는 신청이 생기지 않았다.
    mockMvc
        .perform(
            get("/api/v1/sessions/" + sessionId + "/enrollments/me")
                .header("Authorization", "Bearer " + loginToken(EMPLOYEE_ID)))
        .andExpect(status().isNotFound());
  }

  // 관리자 경로: dev 로그인 토큰의 roles 클레임이 ROLE_ADMIN으로 변환되어 실제 데이터로 통과한다.
  @Test
  void adminTokenFromDevLoginReadsRosterAndEmployeeIsForbidden() throws Exception {
    createSession();
    String roster = "/api/v1/admin/sessions/" + sessionId + "/roster";

    mockMvc
        .perform(get(roster).header("Authorization", "Bearer " + loginToken(ADMIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(sessionId))
        .andExpect(jsonPath("$.capacity").value(5));
    mockMvc
        .perform(get(roster).header("Authorization", "Bearer " + loginToken(EMPLOYEE_ID)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  private void createSession() {
    Course course = courseRepository.save(new Course("보안 통합 테스트 과정", "테스트", "삭제될 데이터"));
    courseId = course.getId();
    CourseSession session =
        sessionRepository.save(
            new CourseSession(course, Instant.now().plus(30, ChronoUnit.DAYS), "테스트실", 5));
    sessionId = session.getId();
  }

  private String loginToken(String employeeId) throws Exception {
    String body = login(employeeId).andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }

  // W1: 공개 경로라도 만료·위조 토큰이 오면 401이다. 프런트 인터셉터가 처리한다(계획 T4).
  @Test
  void expiredTokenOnPublicPathIsUnauthorizedButNoTokenIsOk() throws Exception {
    Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
    String token = sign(jwtSecretKey, EMPLOYEE_ID, List.of("EMPLOYEE"), past, past.plusSeconds(60));

    mockMvc
        .perform(get("/api/v1/courses").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
  }

  // W2: alg=none 토큰은 거부한다.
  @Test
  void algNoneTokenIsUnauthorized() throws Exception {
    String token = compact("{\"alg\":\"none\"}", payload(Instant.now().plusSeconds(600))) + ".";

    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }

  // W2: 같은 키로 HS512 서명해도 HS256만 허용하므로 거부한다.
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
        .perform(
            post("/api/v1/sessions/1/enrollments")
                .header("Authorization", "Bearer " + signingInput + "." + signature))
        .andExpect(status().isUnauthorized());
  }

  // W2: clock skew(기본 60초) 밖으로 만료된 토큰은 거부한다.
  @Test
  void tokenExpiredBeyondClockSkewIsUnauthorized() throws Exception {
    Instant now = Instant.now();
    String token =
        sign(
            jwtSecretKey,
            EMPLOYEE_ID,
            List.of("EMPLOYEE"),
            now.minus(10, ChronoUnit.MINUTES),
            now.minus(2, ChronoUnit.MINUTES));

    mockMvc
        .perform(post("/api/v1/sessions/1/enrollments").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  private static String payload(Instant expiresAt) {
    return "{\"sub\":\""
        + EMPLOYEE_ID
        + "\",\"roles\":[\"EMPLOYEE\"],\"exp\":"
        + expiresAt.getEpochSecond()
        + "}";
  }

  private static String compact(String headerJson, String payloadJson) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
        + "."
        + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
  }

  private ResultActions login(String employeeId) throws Exception {
    return mockMvc.perform(
        post("/api/v1/dev/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"employeeId\":\"" + employeeId + "\"}"));
  }

  private String bearer(String employeeId) {
    return "Bearer " + tokenIssuer.issue(employeeId).accessToken();
  }

  private static String sign(
      SecretKey key, String subject, List<String> roles, Instant issuedAt, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("eduhub-dev")
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", roles)
            .build();
    return new NimbusJwtEncoder(new ImmutableSecret<>(key))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
