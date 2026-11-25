package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 실제 delegating bcrypt 인코더가 72바이트를 넘는 입력을 다루는 방식을 기록한다. 그래서 요청 검증이 바이트 길이로 막는다. */
class PasswordEncoderLimitTest {

  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  @Test
  void encodeRejectsMoreThan72Bytes() {
    assertThat(encoder.encode("a".repeat(72))).startsWith("{bcrypt}");
    assertThatThrownBy(() -> encoder.encode("a".repeat(73)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void matchesSilentlyIgnoresBytesBeyond72() {
    String hash = encoder.encode("a".repeat(72));

    assertThat(encoder.matches("a".repeat(73), hash)).isTrue();
  }
}
