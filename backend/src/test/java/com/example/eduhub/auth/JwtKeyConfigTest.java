package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** 키 처리 규칙(설계 5절)과 무작위 키 허용 조건. 설계 8절의 A13. */
class JwtKeyConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestBeans.class, JwtKeyConfig.class, TokenIssuer.class);

  @Configuration
  static class TestBeans {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }

  private static Employee employee(long id, String no) {
    Employee employee = new Employee(no, "{bcrypt}x", EmployeeRole.EMPLOYEE, Instant.now());
    ReflectionTestUtils.setField(employee, "id", id);
    return employee;
  }

  private static String randomKey(int bytes) {
    byte[] raw = new byte[bytes];
    new SecureRandom().nextBytes(raw);
    return Base64.getEncoder().encodeToString(raw);
  }

  // A13
  @Test
  void emptyKeyFailsStartupWhenRandomKeyIsNotAllowed() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=", "eduhub.auth.allow-random-key=false")
        .run(context -> assertThat(context).hasFailed());
  }

  // A13
  @Test
  void missingKeyFailsStartupWhenRandomKeyIsNotAllowed() {
    runner.run(context -> assertThat(context).hasFailed());
  }

  // A13
  @Test
  void shortKeyFailsStartupAndDoesNotLeakKey() {
    String shortKey = randomKey(16);
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=" + shortKey)
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(rootMessages(context.getStartupFailure())).doesNotContain(shortKey);
        });
  }

  @Test
  void shortKeyFailsEvenWhenRandomKeyIsAllowed() {
    runner
        .withPropertyValues(
            "eduhub.auth.jwt-secret=" + randomKey(16), "eduhub.auth.allow-random-key=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void invalidBase64FailsStartupAndDoesNotLeakKey() {
    String bad = "not base64 !!! value";
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=" + bad)
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(rootMessages(context.getStartupFailure())).doesNotContain(bad);
        });
  }

  // A13
  @Test
  void emptyKeyStartsWithRandomKeyWhenRandomKeyIsAllowed() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=", "eduhub.auth.allow-random-key=true")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(JwtDecoder.class);
          assertThat(context).hasSingleBean(JwtEncoder.class);
          // 만든 키로 발급한 토큰을 같은 컨텍스트의 디코더가 검증한다.
          String token = context.getBean(TokenIssuer.class).issue(employee(1L, "E1")).token();
          assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("1");
        });
  }

  @Test
  void configuredKeyStartsAndIssuesVerifiableToken() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=" + randomKey(32))
        .run(context -> {
          assertThat(context).hasNotFailed();
          String token = context.getBean(TokenIssuer.class).issue(employee(2L, "E2")).token();
          assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("2");
        });
  }

  private static String rootMessages(Throwable failure) {
    StringBuilder sb = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      sb.append(t.getMessage()).append('\n');
    }
    return sb.toString();
  }
}
