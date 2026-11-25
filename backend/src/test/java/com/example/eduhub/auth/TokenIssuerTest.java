package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class TokenIssuerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T01:00:00Z");

  private final SecretKey key = randomKey();

  private TokenIssuer issuer(Duration ttl) {
    return new TokenIssuer(
        new NimbusJwtEncoder(new ImmutableSecret<>(key)),
        new AuthProperties(null, ttl, true),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static SecretKey randomKey() {
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    return new SecretKeySpec(raw, "HmacSHA256");
  }

  private static Employee employee(long id, String no, EmployeeRole role) {
    Employee employee = new Employee(no, "{bcrypt}x", role, NOW);
    ReflectionTestUtils.setField(employee, "id", id);
    return employee;
  }

  @Test
  void tokenCarriesIdSubjectAndRoleClaims() {
    TokenIssuer.IssuedToken issued =
        issuer(Duration.ofMinutes(60)).issue(employee(3L, "E1001", EmployeeRole.ADMIN));
    Jwt jwt = decodeIgnoringExpiry(issued.token());

    assertThat(jwt.getSubject()).isEqualTo("3");
    assertThat(jwt.getClaimAsString("employee_no")).isEqualTo("E1001");
    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("eduhub");
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
  }

  @Test
  void expiryIsIssuedAtPlusTtl() {
    TokenIssuer.IssuedToken issued =
        issuer(Duration.ofMinutes(60)).issue(employee(3L, "E1001", EmployeeRole.EMPLOYEE));
    Jwt jwt = decodeIgnoringExpiry(issued.token());

    assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
    assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    assertThat(issued.expiresIn()).isEqualTo(3600);
  }

  @Test
  void issuedTokenToStringHidesToken() {
    TokenIssuer.IssuedToken issued =
        issuer(Duration.ofMinutes(60)).issue(employee(3L, "E1001", EmployeeRole.EMPLOYEE));

    assertThat(issued.toString()).doesNotContain(issued.token());
  }

  private Jwt decodeIgnoringExpiry(String token) {
    NimbusJwtDecoder lenient =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    lenient.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    return lenient.decode(token);
  }
}
