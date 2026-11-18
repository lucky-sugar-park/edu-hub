package com.example.eduhub.common;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 서명 키와 인코더·디코더(HS256, D-30).
 *
 * <p>키는 Base64 디코드 결과가 32바이트 이상이어야 한다. 비어 있으면 dev 로그인이 켜진 프로필에서만 무작위 키를 만들고(재시작하면 기존 토큰 무효), 그 밖에는
 * 기동을 실패시킨다. 키 값은 로그와 예외 메시지에 쓰지 않는다.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtKeyConfig {

  static final int MIN_KEY_BYTES = 32;
  private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

  @Bean
  public SecretKey jwtSecretKey(AuthProperties properties) {
    String secret = properties.jwtSecret();
    byte[] bytes;
    if (secret == null || secret.isBlank()) {
      if (!properties.devLoginEnabled()) {
        throw new IllegalStateException(
            "eduhub.auth.jwt-secret(EDUHUB_JWT_SECRET)이 비어 있습니다. dev 로그인이 꺼진 환경에서는 Base64 인코딩 "
                + MIN_KEY_BYTES
                + "바이트 이상의 키가 필요합니다");
      }
      bytes = new byte[MIN_KEY_BYTES];
      new SecureRandom().nextBytes(bytes);
      log.warn("JWT 서명 키가 설정되지 않아 무작위 키를 만들었습니다. 재시작하면 기존 토큰이 무효가 됩니다(개발용)");
    } else {
      try {
        bytes = Base64.getDecoder().decode(secret.trim());
      } catch (IllegalArgumentException e) {
        // 원인 예외 메시지에 값이 들어갈 수 있어 연결하지 않는다.
        throw new IllegalStateException("eduhub.auth.jwt-secret이 올바른 Base64가 아닙니다");
      }
      if (bytes.length < MIN_KEY_BYTES) {
        throw new IllegalStateException(
            "eduhub.auth.jwt-secret은 Base64 디코드 결과가 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다");
      }
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
    // iss는 검증하지 않는다. SSO 연동 시 발급자가 바뀌므로 의도적으로 생략한다(D-30).
    return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }
}
