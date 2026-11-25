package com.example.eduhub.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 토큰에서 꺼낸 현재 사용자(D-33). 사번은 헤더·쿼리·본문이 아니라 토큰 클레임에서만 읽는다.
 *
 * @param id 사원 ID(토큰의 {@code sub})
 * @param employeeNo 사번(토큰의 {@code employee_no})
 * @param role 역할(토큰의 {@code role})
 */
public record CurrentUser(long id, String employeeNo, EmployeeRole role) {

  /** 클레임이 빠졌거나 형식이 틀리면 401로 이어지는 {@link AuthenticationCredentialsNotFoundException}을 던진다. */
  public static CurrentUser from(Jwt jwt) {
    if (jwt == null) {
      throw invalid();
    }
    try {
      String subject = jwt.getSubject();
      String employeeNo = jwt.getClaimAsString("employee_no");
      String role = jwt.getClaimAsString("role");
      if (subject == null || employeeNo == null || employeeNo.isBlank() || role == null) {
        throw invalid();
      }
      return new CurrentUser(Long.parseLong(subject), employeeNo, EmployeeRole.valueOf(role));
    } catch (AuthenticationCredentialsNotFoundException e) {
      throw e;
    } catch (RuntimeException e) {
      // 클레임 값이 메시지에 들어가지 않도록 원인을 연결하지 않는다. 클레임 변환 실패(예: 목록)도 여기로 온다.
      throw invalid();
    }
  }

  private static AuthenticationCredentialsNotFoundException invalid() {
    return new AuthenticationCredentialsNotFoundException("토큰의 사용자 정보가 올바르지 않습니다");
  }
}
