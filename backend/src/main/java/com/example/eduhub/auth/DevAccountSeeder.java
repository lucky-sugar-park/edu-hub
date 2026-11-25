package com.example.eduhub.auth;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 개발용 계정(E1001·E1002 사원, A0001 관리자)을 만든다(D-33). dev 프로필에서만 동작한다.
 *
 * <p>비밀번호는 환경 변수 {@code EDUHUB_DEV_PASSWORD}(세 계정 공통)로만 받는다. 비어 있거나 BCrypt 한계(UTF-8 72바이트)를 넘으면 계정을
 * 만들지 않고 경고만 남긴다. 이미 있는 계정은 건드리지 않으므로 다시 실행해도 중복이 없다. 비밀번호와 해시는 로그에 쓰지 않는다.
 */
@Component
@Profile("dev")
public class DevAccountSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

  record Account(String employeeNo, EmployeeRole role) {}

  static final List<Account> ACCOUNTS =
      List.of(
          new Account("E1001", EmployeeRole.EMPLOYEE),
          new Account("E1002", EmployeeRole.EMPLOYEE),
          new Account("A0001", EmployeeRole.ADMIN));

  private final EmployeeRepository employeeRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;
  private final String password;

  public DevAccountSeeder(
      EmployeeRepository employeeRepository,
      PasswordEncoder passwordEncoder,
      Clock clock,
      PlatformTransactionManager transactionManager,
      @Value("${EDUHUB_DEV_PASSWORD:}") String password) {
    this.employeeRepository = employeeRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (password == null || password.isBlank()) {
      log.warn("EDUHUB_DEV_PASSWORD가 비어 있어 개발용 계정을 만들지 않습니다");
      return;
    }
    if (!MaxUtf8BytesValidator.withinLimit(password, 72)) {
      log.warn("EDUHUB_DEV_PASSWORD가 UTF-8 72바이트를 넘어 개발용 계정을 만들지 않습니다");
      return;
    }
    try {
      Integer created = transactionTemplate.execute(status -> createMissing());
      log.info("개발용 계정 {}개를 만들었습니다", created);
    } catch (DataIntegrityViolationException e) {
      // 다른 인스턴스가 먼저 만든 경우. 원인 메시지에 값이 들어갈 수 있어 남기지 않는다.
      log.warn("개발용 계정 생성 중 사번이 겹쳐 전체를 되돌렸습니다. 이번 실행에서는 개발용 계정을 만들지 않았습니다");
    }
  }

  private int createMissing() {
    int created = 0;
    for (Account account : ACCOUNTS) {
      if (!employeeRepository.existsByEmployeeNo(account.employeeNo())) {
        employeeRepository.save(
            new Employee(
                account.employeeNo(),
                passwordEncoder.encode(password),
                account.role(),
                clock.instant()));
        created++;
      }
    }
    employeeRepository.flush();
    return created;
  }
}
