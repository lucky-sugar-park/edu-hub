package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentEmployeeArgumentResolverTest {

  private final CurrentEmployeeArgumentResolver resolver = new CurrentEmployeeArgumentResolver();

  @SuppressWarnings("unused")
  void handler(@CurrentEmployee String annotated, String plain, @CurrentEmployee Long wrongType) {}

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private MethodParameter param(int index) throws Exception {
    var method =
        CurrentEmployeeArgumentResolverTest.class.getDeclaredMethod(
            "handler", String.class, String.class, Long.class);
    return new MethodParameter(method, index);
  }

  private Object resolve() throws Exception {
    return resolver.resolveArgument(
        param(0), null, new ServletWebRequest(new MockHttpServletRequest()), null);
  }

  /** 주체 이름(name)을 {@code subject}로 하는 JWT 인증을 SecurityContext에 둔다. */
  private static void login(String subject) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .claim("roles", List.of("EMPLOYEE"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")), subject));
  }

  @Test
  void resolvesSubjectOfJwtAsIs() throws Exception {
    login("E1001");
    assertThat(resolve()).isEqualTo("E1001");
  }

  @Test
  void acceptsExactlyTwentyCharacters() throws Exception {
    login("A".repeat(20));
    assertThat(resolve()).isEqualTo("A".repeat(20));
  }

  @Test
  void rejectsWhenNoAuthentication() {
    assertThatThrownBy(this::resolve)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void rejectsNonJwtAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("E1001", "x", "ROLE_EMPLOYEE"));
    assertThatThrownBy(this::resolve)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void rejectsBlankSubject() {
    login("   ");
    assertThatThrownBy(this::resolve)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void rejectsMissingSubject() {
    login(null);
    assertThatThrownBy(this::resolve)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void supportsOnlyAnnotatedStringParameters() throws Exception {
    assertThat(resolver.supportsParameter(param(0))).isTrue();
    assertThat(resolver.supportsParameter(param(1))).isFalse();
    assertThat(resolver.supportsParameter(param(2))).isFalse();
  }
}
