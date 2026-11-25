package com.example.eduhub.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * {@code @WebMvcTest} 슬라이스용 보안 설정. 실제 {@link SecurityConfig}(규칙표, 401·403 응답)를 가져오고, 서명 키 없이 쓰도록 모든
 * 토큰 문자열을 거부하는 디코더를 둔다. 인증은 {@link #employee}·{@link #admin}의 {@code jwt()} 후처리기로 만든다.
 *
 * <p>슬라이스에는 {@code @ActiveProfiles("test")}를 붙이지 않는다(붙이면 시더·무작위 키 설정이 켜져 슬라이스에 없는 빈이 필요해진다).
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class WebMvcSecurityTestConfig {

  @Bean
  JwtDecoder rejectingJwtDecoder() {
    return token -> {
      throw new BadJwtException("테스트 슬라이스는 토큰 문자열을 검증하지 않는다");
    };
  }

  /** 사번이 {@code employeeNo}인 일반 사원(사원 ID 1, role EMPLOYEE, 권한 ROLE_EMPLOYEE). */
  public static JwtRequestPostProcessor employee(String employeeNo) {
    return jwt()
        .jwt(j -> j.subject("1").claim("employee_no", employeeNo).claim("role", "EMPLOYEE"))
        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
  }

  /** 사번이 {@code employeeNo}인 관리자(사원 ID 2, role ADMIN, 권한 ROLE_ADMIN). */
  public static JwtRequestPostProcessor admin(String employeeNo) {
    return jwt()
        .jwt(j -> j.subject("2").claim("employee_no", employeeNo).claim("role", "ADMIN"))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
