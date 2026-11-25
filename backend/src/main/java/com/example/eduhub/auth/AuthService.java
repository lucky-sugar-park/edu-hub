package com.example.eduhub.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 사번과 비밀번호로 로그인한다(D-33).
 *
 * <p>사번 조회는 DB 콜레이션상 대소문자를 구분하지 않는다(예: {@code e1001}과 {@code E1001}은 같은 계정).
 *
 * <p>사번이 없을 때도 더미 해시로 {@code matches}를 한 번 돌려 응답 시간으로 사번 존재 여부가 드러나지 않게 한다. 비밀번호·해시·토큰은 로그에 쓰지 않는다.
 *
 * <p>클래스에 트랜잭션을 걸지 않는다. 사번 조회는 리포지토리의 짧은 읽기 트랜잭션으로 끝나고, 수십~수백 ms 걸리는 BCrypt 비교는 DB 커넥션을 쥐지 않은 채
 * 한다(공개 로그인 요청이 몰려도 커넥션 풀이 고갈되지 않게). {@link Employee}는 지연 로딩 필드가 없어 트랜잭션 밖에서 읽어도 된다.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final EmployeeRepository employeeRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;
  private final String dummyHash;

  public AuthService(
      EmployeeRepository employeeRepository,
      PasswordEncoder passwordEncoder,
      TokenIssuer tokenIssuer) {
    this.employeeRepository = employeeRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenIssuer = tokenIssuer;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  public LoginResponse login(String employeeNo, String password) {
    Employee employee = employeeRepository.findByEmployeeNo(employeeNo).orElse(null);
    if (employee == null) {
      passwordEncoder.matches(password, dummyHash);
      throw failure();
    }
    if (!passwordEncoder.matches(password, employee.getPasswordHash())) {
      throw failure();
    }
    TokenIssuer.IssuedToken issued = tokenIssuer.issue(employee);
    return new LoginResponse(issued.token(), issued.expiresIn(), MeResponse.from(employee));
  }

  private InvalidCredentialsException failure() {
    log.info("로그인 실패");
    return new InvalidCredentialsException();
  }
}
