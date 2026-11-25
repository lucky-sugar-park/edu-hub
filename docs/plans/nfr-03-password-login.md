# NFR-03 사원 계정과 비밀번호 로그인 구현계획

- 상태: **승인됨 (2026-11-25)**. T1부터 진행한다.
- 설계명세: `docs/design/NFR-03-password-login.md` (승인됨). 결정 사항 D-33(D-30 대체), 선행 D-11·D-18.
- 규칙: `CLAUDE.md`, `backend/CLAUDE.md`, `.claude/skills/eduhub-api/SKILL.md`, `.claude/rules/db-migration.md`
- 브랜치: `feature/nfr-03-password-login`
- 진행: 작업마다 implementer 구현 → reviewer 검토. 심각·경고는 implementer가 수정하고, 수정·재검토는 최대 2회.
- 모든 명령은 `backend/`에서 실행한다. 테스트는 로컬 MariaDB `eduhub_test`를 쓴다.
- **테스트 겹침 방지:** reviewer는 테스트를 돌리지 않고 코드와 문서만 본다. 전체 테스트는 검토가 끝난 뒤 한 번만 돌린다(종료 훅과 같은 `backend/build`·`eduhub_test`를 쓰므로).
- **여러 계층 작업(CLAUDE.md 5):** 새 패키지 `auth`(신규 약 12파일), `common`에서 옮기거나 지우는 파일 약 10개, 컨트롤러 1개, 테스트 수정 약 8·신규 약 6, diff 약 1,200줄 예상. 이 계획 승인으로 함께 승인받는다. T3은 모든 인증 경로를 한 번에 바꾸므로 시작 전에 한 번 더 알린다.
- 프런트는 고치지 않는다. 병합 후 프런트 작업 전까지 화면 로그인이 동작하지 않는다(D-33, PR 본문에 적는다).

## 작업 순서

T1 → T2 → T3 → T4 → T5 → 최종 검토(critical-reviewer: 설계명세 + 전체 diff + 1차 검토 결과)

T2까지는 기존 dev 로그인과 새 로그인이 함께 있어 기존 테스트가 그대로 통과해야 한다. T3에서 한 번에 바꾼다.

---

## T1. 스키마, 엔티티, Repository

**변경 파일**
- 신규 `src/main/resources/db/migration/V7__create_employee.sql`: 설계명세 2절 그대로. V1~V6은 수정하지 않는다.
- 신규 `auth/package-info.java`: 사원 계정, 로그인, 토큰 발급·검증, 현재 사용자. `course`·`enrollment`는 `auth`의 `CurrentUser`만 쓴다.
- 신규 `auth/EmployeeRole.java`: enum `EMPLOYEE`, `ADMIN`
- 신규 `auth/Employee.java` (`@Entity`, 테이블 `employee`)
  - 필드 `id`(IDENTITY), `employeeNo`, `passwordHash`, `role`(`@Enumerated(STRING)`), `createdAt`(`Instant`)
  - JPA용 protected 기본 생성자, `Employee(String employeeNo, String passwordHash, EmployeeRole role, Instant createdAt)`, getter
  - `toString`에 `passwordHash`를 넣지 않는다
- 신규 `auth/EmployeeRepository.java`: `JpaRepository<Employee, Long>`, `Optional<Employee> findByEmployeeNo(String)`, `boolean existsByEmployeeNo(String)`

**테스트**
- 신규 `auth/EmployeeRepositoryTest`(`@DataJpaTest` + `replace = NONE` + `@ActiveProfiles("test")`)
  - 저장 후 `findByEmployeeNo`로 읽으면 필드가 같다(role은 문자열로 저장)
  - 같은 `employee_no` 두 번 저장 → `DataIntegrityViolationException`
  - 테스트는 자기 데이터만 지운다(다른 테스트와 DB를 같이 쓴다)

**완료 명령**
```bash
./gradlew test --tests "*EmployeeRepositoryTest"
```

---

## T2. 비밀번호 로그인과 토큰 발급

**변경 파일**
- 신규 `auth/PasswordConfig.java`: `PasswordEncoder` 빈 = `PasswordEncoderFactories.createDelegatingPasswordEncoder()`(기본 bcrypt)
- 신규 `auth/AuthLoginRequest.java`(record): `employeeNo`(`@NotBlank @Size(max=20)`), `password`(`@NotBlank @Size(max=72)`), 메시지는 한국어. `toString`에 비밀번호를 넣지 않는다.
  - 이름은 T3에서 `common/LoginRequest`를 지운 뒤 `LoginRequest`로 바꾼다(T2 동안 OpenAPI 스키마 이름 충돌 방지).
- 신규 `auth/AuthLoginResponse.java`(record): `accessToken`, `expiresIn`(long, 초), `user`(`MeResponse`). T3에서 `LoginResponse`로 바꾼다.
- 신규 `auth/MeResponse.java`(record): `id`, `employeeNo`, `role`
- 신규 `auth/InvalidCredentialsException.java`(`RuntimeException`, 고정 메시지 "사번 또는 비밀번호가 올바르지 않습니다")
- 신규 `auth/AccessTokenIssuer.java`: `issue(Employee)` → 토큰 문자열과 만료 초. 클레임은 설계명세 5절(`sub`=id 문자열, `employee_no`, `role`, `iss`=`eduhub`). 기존 `JwtEncoder`·`Clock`·`AuthProperties.tokenTtl()`을 쓴다. T3에서 기존 `TokenIssuer`를 지우고 이 클래스를 `TokenIssuer`로 바꾼다.
- 신규 `auth/AuthService.java`: `login(employeeNo, password)`
  - `findByEmployeeNo` → 없으면 클래스 상수 `DUMMY_HASH`(기동 시 `passwordEncoder.encode`로 만든 값)로 `matches`를 한 번 돌리고 `InvalidCredentialsException`
  - 비밀번호 불일치도 같은 예외. 실패 로그는 INFO 한 줄, 사번·비밀번호를 넣지 않는다
  - `@Transactional(readOnly = true)`
- 신규 `auth/AuthController.java`: `POST /api/auth/login` → `AuthLoginResponse`. `@Tag(name = "Auth")`, `@Operation`, 200·400·401 `@ApiResponse`
- 수정 `common/GlobalExceptionHandler`: `InvalidCredentialsException` → 401 `ErrorResponse("UNAUTHORIZED", 고정 메시지)`
- 수정 `common/SecurityConfig`: `POST /api/auth/login`은 이미 permitAll이므로 바꾸지 않는다.
- 수정 `application.yml`: 바꾸지 않는다(TTL 변경은 T3).

**테스트**
- 신규 `auth/AuthServiceTest`(Mockito): 성공, 비밀번호 틀림, 사번 없음(둘 다 같은 예외), 사번 없을 때도 `matches`가 1번 호출됨
- 신규 `auth/AccessTokenIssuerTest`: 고정 `Clock`으로 `exp` = `iat` + TTL, 클레임 3개와 `sub`가 id 문자열
- 신규 `auth/AuthControllerTest`(`@WebMvcTest` + `WebMvcSecurityTestConfig`): 200 본문 모양, 401 본문 `UNAUTHORIZED`·고정 메시지, 400(빈 값)
- `OpenApiDocsTest`가 새 엔드포인트 문서를 포함하는지 확인(필요하면 단언 추가)

**완료 명령**
```bash
./gradlew test --tests "*Auth*" --tests "*AccessTokenIssuerTest" --tests "*OpenApiDocsTest"
```

---

## T3. 보안 체인 교체, `CurrentUser`, dev 로그인 제거 (시작 전 알림)

**변경 파일**
- 이동(패키지 `common` → `auth`, 내용 수정 포함)
  - `SecurityConfig`: 설계명세 6절 규칙표로 교체. `JwtGrantedAuthoritiesConverter`의 권한 클레임 `role`, 접두사 `ROLE_`, 주체 `sub`. 401·403 처리기는 그대로.
  - `JwtKeyConfig`: 무작위 키 허용 조건을 `AuthProperties.allowRandomKey()`로 바꾼다. 오류 문구의 "dev 로그인이 꺼진 환경" → "무작위 키가 허용되지 않은 환경"
  - `AuthProperties`: `record AuthProperties(String jwtSecret, Duration tokenTtl, boolean allowRandomKey)`, 기본 TTL 60분, `toString`에 키 없음
  - `AccessTokenIssuer` → `TokenIssuer`, `AuthLoginRequest` → `LoginRequest`, `AuthLoginResponse` → `LoginResponse`
- 삭제: `common/DevAuthController`, `common/LoginRequest`, `common/LoginResponse`, `common/TokenIssuer`, `common/AdminProperties`, `common/CurrentEmployee`, `common/CurrentEmployeeArgumentResolver`
- 수정 `common/WebConfig`: 리졸버 등록과 `@EnableConfigurationProperties(AdminProperties.class)` 제거. 남는 내용이 없으면 파일 삭제
- 신규 `auth/CurrentUser.java`: `record CurrentUser(long id, String employeeNo, EmployeeRole role)`, `static CurrentUser from(Jwt jwt)`. `sub`가 숫자가 아니거나 `employee_no`·`role`이 없거나 알 수 없는 값이면 `AuthenticationCredentialsNotFoundException`
- 신규 `auth/MeController.java`: `GET /api/me` → `MeResponse`(토큰만). `@SecurityRequirement(name = "bearerAuth")`, 200·401 문서
- 수정 `enrollment/EnrollmentController`: 네 엔드포인트의 `@Parameter(hidden = true) @CurrentEmployee String employeeId` → `@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt`, 본문 첫 줄 `String employeeNo = CurrentUser.from(jwt).employeeNo();`. 서비스 호출은 그대로
- 수정 `application.yml`: `eduhub.admin` 삭제, `eduhub.auth.token-ttl: 60m`, `dev-login-enabled` → `allow-random-key: false`
- 수정 `application-dev.yml`, `application-test.yml`: `dev-login-enabled: true` → `allow-random-key: true`, test의 `eduhub.admin.employee-ids` 삭제
- 수정 `.env.example`: `EDUHUB_ADMIN_EMPLOYEE_IDS=` 줄 삭제(이름만 있는 줄, 비밀 값 없음)
- 수정 `.claude/launch.json`: `backend-8088`의 `--eduhub.admin.employee-ids=ADMIN01` 인자 삭제

**테스트**
- 이동·수정 `common/WebMvcSecurityTestConfig` → `auth/WebMvcSecurityTestConfig`
  - `employee(String employeeNo)`: `sub`="1", `employee_no`=employeeNo, `role`="EMPLOYEE", 권한 `ROLE_EMPLOYEE`
  - `admin(String employeeNo)`: `sub`="2", `role`="ADMIN", 권한 `ROLE_ADMIN`
  - 슬라이스에 `@ActiveProfiles("test")`를 붙이지 않는다는 주석은 "dev 로그인" 대신 "시더·무작위 키"로 고친다
- 수정 `CourseAdminControllerTest`, `EnrollmentAdminControllerTest`, `EnrollmentControllerTest`: import 경로만 바뀐다. 관리자 테스트가 `ROLE_EMPLOYEE`를 기대했다면 고친다
- 수정 `SecurityIntegrationTest`(→ `auth/`): 실제 서명 토큰으로 A5(없음·위조·만료 401), A9(과정 등록 401/403/201), A10(관리자 API 403), A11(공개 GET 200), 토큰의 `role` → 권한 변환
- 신규 `auth/CurrentUserTest`: 정상 클레임, `sub` 숫자 아님, `employee_no` 없음, 알 수 없는 `role`
- 신규 `auth/MeControllerTest`: A6, 토큰 없음 401
- 수정 `JwtKeyConfigTest`(→ `auth/`): `allowRandomKey` 기준으로
- 삭제 `DevLoginDisabledIntegrationTest`, `AdminPropertiesTest`, `CurrentEmployeeArgumentResolverTest`, 기존 `TokenIssuerTest`(새 `TokenIssuerTest`가 대신)

**완료 명령**
```bash
./gradlew test --tests "com.example.eduhub.auth.*" --tests "*ControllerTest" --tests "*OpenApiDocsTest"
```

---

## T4. 개발용 계정 시더

**변경 파일**
- 신규 `auth/DevAccountSeeder.java`: `@Profile("dev")`, `ApplicationRunner`
  - 비밀번호는 `@Value("${EDUHUB_DEV_PASSWORD:}")`. 비어 있으면 WARN 한 줄(값 없음)만 남기고 끝
  - E1001·E1002(EMPLOYEE), A0001(ADMIN) 중 `existsByEmployeeNo`가 거짓인 것만 `passwordEncoder.encode`로 저장. 만든 개수를 INFO로 남긴다(비밀번호 없이)
  - 트랜잭션 하나. 동시에 두 인스턴스가 뜨는 경우는 `uk_employee_no`가 막는다(예외는 WARN으로 남기고 기동은 계속)
  - 비밀번호가 UTF-8 72바이트를 넘으면 `encode`가 `IllegalArgumentException`을 던지므로(T2 `PasswordEncoderLimitTest`로 확인), 미리 바이트 길이를 검사해 WARN만 남기고 계정을 만들지 않는다. 테스트에 이 경우를 추가한다
- 수정 `.env.example`: `EDUHUB_DEV_PASSWORD=` 이름만 추가(개발용 계정 공통 비밀번호, dev 프로필에서만 사용)

**테스트**
- 신규 `auth/DevAccountSeederTest`(`@DataJpaTest` + 시더를 직접 생성해 `run` 호출, `@ActiveProfiles("test")`): A12 세 경우(비밀번호 있음 → 3개, 없음 → 0개, 두 번 실행 → 3개 그대로). 테스트 계정 사번만 정리한다

**완료 명령**
```bash
./gradlew test --tests "*DevAccountSeederTest"
```

---

## T5. 통합 테스트와 문서

**변경 파일**
- 신규 `auth/AuthIntegrationTest`(`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`, 실제 DB·실제 서명)
  - A1~A4: 계정을 BCrypt로 넣고 로그인. A1은 응답 토큰을 디코드해 `sub`·`role`, `expiresIn` 3600 확인. A2·A3 본문이 같다
  - A7: 로그인 토큰으로 신청 → `enrollment.employee_id`가 그 사원의 사번
  - A8: 사원 B 토큰으로 사원 A만 신청한 회차 취소 → 404
  - 데이터는 테스트 고유 사번·과정으로 만들고 끝에 자기 데이터만 지운다
- 수정 `backend/CLAUDE.md`: 인증(D-33) 절, 프로필과 환경 변수 절(`EDUHUB_DEV_PASSWORD`, `allow-random-key`, 관리자 목록 삭제), 컨트롤러 테스트 도우미 설명, `@CurrentEmployee` → `CurrentUser.from(jwt)`
- 수정 `docs/requirements.md`: 이미 D-33 추가됨. 바꿀 것 없음
- 수정 `README.md`: 확인용 curl(설계명세 8절)과 `EDUHUB_DEV_PASSWORD` 안내를 짧게 추가

**완료 명령**
```bash
./gradlew check
```
그리고 dev 프로필로 띄워 설계명세 8절 curl 세 개를 확인한다(개발자가 `EDUHUB_DEV_PASSWORD`를 `.env`에 넣은 뒤).

---

## 위험 영역

- **보안 규칙 순서(T3):** `GET /api/v1/courses/**` 공개 규칙이 GET 외 ADMIN 규칙보다 앞에 있어야 한다. A9·A11이 이를 지킨다.
- **로그인 정보 노출(T2):** 사번 존재 여부가 응답 본문·시간으로 드러나지 않게 한다. 비밀번호·해시·토큰을 로그·예외·`toString`에 쓰지 않는다. reviewer가 이 점을 따로 본다.
- **대량 이동(T3):** 패키지 이동과 삭제가 한 번에 일어난다. 컴파일이 깨진 중간 상태로 끝나지 않게 T3은 한 번에 마친다.
- **테스트 DB 공유:** 통합 테스트는 고유 사번·과정을 쓰고 자기 데이터만 지운다. 다른 테스트의 데이터(예: "동시성 테스트 과정")에 기대지 않는다.
- **로그인 중 커넥션 점유(T2 검토 W2, 최종 검토 W2에서 해결):** `AuthService`의 클래스 `@Transactional(readOnly = true)`를 지워, 사번 조회는 리포지토리의 짧은 트랜잭션으로 끝내고 BCrypt 비교는 커넥션 없이 한다. 로그인 속도 제한은 여전히 없다(후속 과제, PR 위험 항목).
- **BCrypt 72바이트(T2 검토 W1, 해결):** `matches`는 72바이트 뒤를 조용히 무시하므로 요청 검증을 UTF-8 72바이트 이하(`@MaxUtf8Bytes`)로 했다.
- **프런트:** 이 브랜치에서는 프런트를 고치지 않는다. `api-client` 재생성도 하지 않는다(프런트 작업 때 함께).
