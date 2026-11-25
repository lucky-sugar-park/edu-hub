package com.example.eduhub.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정(D-33). {@code eduhub.auth}로 지정한다.
 *
 * @param jwtSecret HS256 서명 키(Base64, 디코드 결과 32바이트 이상). 환경 변수 {@code EDUHUB_JWT_SECRET}으로만 준다
 * @param tokenTtl 토큰 유효 시간. 기본 60분
 * @param allowRandomKey 키가 비었을 때 무작위 키로 기동할지. 기본 false이며 dev·test 프로필에서만 true다
 */
@ConfigurationProperties("eduhub.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl, boolean allowRandomKey) {

  public AuthProperties {
    tokenTtl = tokenTtl == null ? Duration.ofMinutes(60) : tokenTtl;
  }

  /** 키 값이 로그나 예외 메시지에 찍히지 않도록 직접 만든다. */
  @Override
  public String toString() {
    return "AuthProperties[tokenTtl=" + tokenTtl + ", allowRandomKey=" + allowRandomKey + "]";
  }
}
