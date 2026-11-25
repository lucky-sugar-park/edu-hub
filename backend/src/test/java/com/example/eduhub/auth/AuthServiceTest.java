package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

  private static final String DUMMY = "{bcrypt}dummy";
  private static final String STORED = "{bcrypt}stored";

  @Mock private EmployeeRepository employeeRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TokenIssuer tokenIssuer;

  private AuthService service;
  private Employee employee;

  @BeforeEach
  void setUp() {
    given(passwordEncoder.encode(anyString())).willReturn(DUMMY);
    service = new AuthService(employeeRepository, passwordEncoder, tokenIssuer);
    employee = new Employee("E1001", STORED, EmployeeRole.EMPLOYEE, Instant.EPOCH);
    ReflectionTestUtils.setField(employee, "id", 3L);
  }

  @Test
  void returnsTokenAndUserOnValidCredentials() {
    given(employeeRepository.findByEmployeeNo("E1001")).willReturn(Optional.of(employee));
    given(passwordEncoder.matches("pw", STORED)).willReturn(true);
    given(tokenIssuer.issue(employee)).willReturn(new TokenIssuer.IssuedToken("token-value", 3600));

    LoginResponse response = service.login("E1001", "pw");

    assertThat(response.accessToken()).isEqualTo("token-value");
    assertThat(response.expiresIn()).isEqualTo(3600);
    assertThat(response.user()).isEqualTo(new MeResponse(3L, "E1001", EmployeeRole.EMPLOYEE));
  }

  @Test
  void rejectsWrongPassword() {
    given(employeeRepository.findByEmployeeNo("E1001")).willReturn(Optional.of(employee));
    given(passwordEncoder.matches("bad", STORED)).willReturn(false);

    assertThatThrownBy(() -> service.login("E1001", "bad"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage(InvalidCredentialsException.MESSAGE);
    verify(tokenIssuer, never()).issue(any());
  }

  @Test
  void rejectsUnknownEmployeeWithSameException() {
    given(employeeRepository.findByEmployeeNo("NONE")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("NONE", "pw"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage(InvalidCredentialsException.MESSAGE);
    verify(tokenIssuer, never()).issue(any());
  }

  @Test
  void matchesDummyHashExactlyOnceWhenEmployeeIsUnknown() {
    given(employeeRepository.findByEmployeeNo("NONE")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("NONE", "pw"))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordEncoder).matches("pw", DUMMY);
    verify(passwordEncoder, org.mockito.Mockito.times(1)).matches(anyString(), anyString());
    verify(passwordEncoder, never()).matches(anyString(), eq(STORED));
  }
}
