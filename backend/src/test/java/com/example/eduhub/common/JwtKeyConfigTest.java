package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** 키 처리 규칙(설계 2절)과 dev 로그인 노출 조건. 설계 8.1의 A7·A8. */
class JwtKeyConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              TestBeans.class, JwtKeyConfig.class, TokenIssuer.class, DevAuthController.class);

  @Configuration
  @EnableConfigurationProperties(AdminProperties.class)
  static class TestBeans {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }

  private static String randomKey(int bytes) {
    byte[] raw = new byte[bytes];
    new SecureRandom().nextBytes(raw);
    return Base64.getEncoder().encodeToString(raw);
  }

  // A7
  @Test
  void devAuthControllerIsAbsentWhenDevLoginIsOff() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=" + randomKey(32))
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(DevAuthController.class);
        });
  }

  @Test
  void devAuthControllerExistsWhenDevLoginIsOn() {
    runner
        .withPropertyValues(
            "eduhub.auth.jwt-secret=" + randomKey(32), "eduhub.auth.dev-login-enabled=true")
        .run(context -> assertThat(context).hasSingleBean(DevAuthController.class));
  }

  // A8
  @Test
  void emptyKeyFailsStartupWhenDevLoginIsOff() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=", "eduhub.auth.dev-login-enabled=false")
        .run(context -> assertThat(context).hasFailed());
  }

  // A8
  @Test
  void missingKeyFailsStartupWhenDevLoginIsOff() {
    runner.run(context -> assertThat(context).hasFailed());
  }

  // A8
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
  void shortKeyFailsEvenWhenDevLoginIsOn() {
    runner
        .withPropertyValues(
            "eduhub.auth.jwt-secret=" + randomKey(16), "eduhub.auth.dev-login-enabled=true")
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

  // A8
  @Test
  void emptyKeyStartsWithRandomKeyWhenDevLoginIsOn() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=", "eduhub.auth.dev-login-enabled=true")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(JwtDecoder.class);
          assertThat(context).hasSingleBean(JwtEncoder.class);
          // 만든 키로 발급한 토큰을 같은 컨텍스트의 디코더가 검증한다.
          String token = context.getBean(TokenIssuer.class).issue("E1").accessToken();
          assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("E1");
        });
  }

  @Test
  void configuredKeyStartsAndIssuesVerifiableToken() {
    runner
        .withPropertyValues("eduhub.auth.jwt-secret=" + randomKey(32))
        .run(context -> {
          assertThat(context).hasNotFailed();
          String token = context.getBean(TokenIssuer.class).issue("E2").accessToken();
          assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("E2");
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
