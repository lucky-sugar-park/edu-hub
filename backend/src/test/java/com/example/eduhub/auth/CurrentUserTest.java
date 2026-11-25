package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

  private static Jwt jwt(Consumer<Jwt.Builder> customizer) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    customizer.accept(builder);
    return builder.build();
  }

  @Test
  void readsIdEmployeeNoAndRoleFromClaims() {
    Jwt jwt = jwt(b -> b.subject("3").claim("employee_no", "E1001").claim("role", "ADMIN"));

    assertThat(CurrentUser.from(jwt)).isEqualTo(new CurrentUser(3L, "E1001", EmployeeRole.ADMIN));
  }

  @Test
  void rejectsNonNumericSubject() {
    Jwt jwt = jwt(b -> b.subject("E1001").claim("employee_no", "E1001").claim("role", "EMPLOYEE"));

    assertThatThrownBy(() -> CurrentUser.from(jwt))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void rejectsMissingEmployeeNo() {
    Jwt jwt = jwt(b -> b.subject("3").claim("role", "EMPLOYEE"));

    assertThatThrownBy(() -> CurrentUser.from(jwt))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void rejectsMissingRole() {
    Jwt jwt = jwt(b -> b.subject("3").claim("employee_no", "E1001"));

    assertThatThrownBy(() -> CurrentUser.from(jwt))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void rejectsUnknownRoleWithoutLeakingValue() {
    Jwt jwt = jwt(b -> b.subject("3").claim("employee_no", "E1001").claim("role", "SUPERUSER"));

    assertThatThrownBy(() -> CurrentUser.from(jwt))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
        .hasMessageNotContaining("SUPERUSER");
  }

  @Test
  void rejectsRoleClaimThatIsAList() {
    Jwt jwt =
        jwt(b ->
            b.subject("3")
                .claim("employee_no", "E1001")
                .claim("role", java.util.List.of("EMPLOYEE", "ADMIN")));

    assertThatThrownBy(() -> CurrentUser.from(jwt))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
        .hasMessageNotContaining("ADMIN");
  }

  @Test
  void rejectsNullJwt() {
    assertThatThrownBy(() -> CurrentUser.from(null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }
}
