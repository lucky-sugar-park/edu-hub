package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenIssuerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T01:00:00Z");

  private final SecretKey key = randomKey();
  private final JwtDecoder decoder = new JwtKeyConfig().jwtDecoder(key);

  private TokenIssuer issuer(Duration ttl, String... adminIds) {
    return new TokenIssuer(
        new NimbusJwtEncoder(new ImmutableSecret<>(key)),
        new AdminProperties(List.of(adminIds)),
        new AuthProperties(null, ttl, true),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static SecretKey randomKey() {
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    return new SecretKeySpec(raw, "HmacSHA256");
  }

  @Test
  void tokenCarriesSubjectIssuerAndTimes() {
    LoginResponse response = issuer(Duration.ofHours(8)).issue("E1001");
    // 고정 시계 기준이라 만료 검증(현재 실제 시각)을 피하려 서명·클레임만 읽는다.
    Jwt jwt = decodeIgnoringExpiry(response.accessToken());

    assertThat(jwt.getSubject()).isEqualTo("E1001");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("eduhub-dev");
    assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
    assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
  }

  @Test
  void responseDescribesTheToken() {
    LoginResponse response = issuer(Duration.ofMinutes(30)).issue("E1001");

    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.employeeId()).isEqualTo("E1001");
    assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
  }

  @Test
  void ordinaryEmployeeGetsEmployeeRoleOnly() {
    LoginResponse response = issuer(Duration.ofHours(8), "TEST-ADMIN").issue("E1001");

    assertThat(response.roles()).containsExactly("EMPLOYEE");
    assertThat(decodeIgnoringExpiry(response.accessToken()).getClaimAsStringList("roles"))
        .containsExactly("EMPLOYEE");
  }

  @Test
  void listedAdminGetsAdminRoleToo() {
    LoginResponse response = issuer(Duration.ofHours(8), "TEST-ADMIN").issue("TEST-ADMIN");

    assertThat(response.roles()).containsExactly("EMPLOYEE", "ADMIN");
    assertThat(decodeIgnoringExpiry(response.accessToken()).getClaimAsStringList("roles"))
        .containsExactly("EMPLOYEE", "ADMIN");
  }

  @Test
  void adminMatchIsExact() {
    assertThat(issuer(Duration.ofHours(8), "TEST-ADMIN").issue("test-admin").roles())
        .containsExactly("EMPLOYEE");
  }

  @Test
  void expiredTokenIsRejectedByDecoder() {
    // 실제 시각보다 한참 전에 만료되는 토큰은 디코더가 거부한다.
    TokenIssuer past =
        new TokenIssuer(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)),
            new AdminProperties(List.of()),
            new AuthProperties(null, Duration.ofMinutes(1), true),
            Clock.fixed(NOW, ZoneOffset.UTC));

    String token = past.issue("E1001").accessToken();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
  }

  private Jwt decodeIgnoringExpiry(String token) {
    NimbusJwtDecoder lenient =
        NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
            .build();
    lenient.setJwtValidator(
        jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
    return lenient.decode(token);
  }
}
