package com.example.eduhub.common;

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
 * <p>슬라이스에는 {@code @ActiveProfiles("test")}를 붙이지 않는다(붙이면 dev 로그인 컨트롤러 조건이 참이 되어 {@code
 * TokenIssuer}가 필요하다).
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

  /** 사번이 {@code employeeId}인 일반 사원(ROLE_EMPLOYEE). */
  public static JwtRequestPostProcessor employee(String employeeId) {
    return jwt()
        .jwt(j -> j.subject(employeeId))
        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
  }

  /** 관리자(ROLE_EMPLOYEE, ROLE_ADMIN). */
  public static JwtRequestPostProcessor admin(String employeeId) {
    return jwt()
        .jwt(j -> j.subject(employeeId))
        .authorities(
            new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
